package zio.compress

import zio._
import zio.compress.ArchiveEntry.{ArchiveEntryFromUnderlying, ArchiveEntryToUnderlying}
import zio.compress.JavaIoInterop._
import zio.compress.zip._
import zio.compress.Zip._
import zio.stream._

import java.io.IOException
import java.nio.file.attribute.FileTime
import java.util.zip.{CRC32, ZipEntry, ZipInputStream, ZipOutputStream}

/** Constructors for [[ZipArchiver]], and `archive` for the default options. */
object ZipArchiver {

  /** An [[Archiver]] for ZIP, based on the JVM standard library.
    *
    * The archive entries do not require an uncompressed size.
    *
    * ⚠️WARNING: High memory usage for zip-method Stored!
    *
    * This archiver needs to slurp each content in memory to calculate a checksum (CRC-32) as the wrapped
    * ZipOutputStream requires it up front. Use the Zip4JArchiver when this is an issue.
    *
    * @param level
    *   compression level (only applicable for method 'deflated'). Defaults to `None`, which uses zlib's default level, 6.
    * @param zipMethod
    *   zip method: stored (no compression) or deflated. Defaults to deflated.
    */
  def apply(
    level: Option[DeflateCompressionLevel] = None,
    zipMethod: ZipMethod = ZipMethod.Deflated,
  ): ZipArchiver =
    new ZipArchiver(level, zipMethod)

  /** See [[apply]] and [[Archiver.archive]]. */
  def archive: ZPipeline[Any, Throwable, (ArchiveEntry[Option, Any], ZStream[Any, Throwable, Byte]), Byte] =
    apply().archive
}

/** An [[Archiver]] that writes ZIP archives with the JVM standard library (`java.util.zip.ZipOutputStream`).
  *
  * Use it to write a ZIP archive whose entries have no known size: it takes `ArchiveEntry[Option, _]`, so the content
  * can be streamed. It writes no encryption; use `Zip4JArchiver` for password-protected archives. It does not compare
  * the declared `uncompressedSize` with the content. For a directory entry it reads the content stream to its end,
  * ignores the bytes, and writes a stored entry of size 0. It writes the `lastModified`, `lastAccess` and `creation`
  * times of an entry when they are set. Make one with `ZipArchiver.apply`, or use `ZipArchiver.archive` for the default
  * options.
  *
  * {{{
  * import zio.compress._
  * import zio.stream._
  *
  * val opening = "Emma Woodhouse, handsome, clever, and rich".getBytes("UTF-8")
  *
  * val entries = ZStream(
  *   ArchiveEntry[Option, Any]("books", isDirectory = true) -> ZStream.empty,
  *   ArchiveEntry[Option, Any]("books/emma.txt")             -> ZStream.fromIterable(opening),
  * )
  *
  * val zipBytes: ZStream[Any, Throwable, Byte] = entries.via(ZipArchiver().archive)
  * }}}
  *
  * @see
  *   [[https://zio.dev/zio-streams-compress/reference-zip/ ZIO Streams Compress: zip reference]]
  */
final class ZipArchiver private (
  level: Option[DeflateCompressionLevel],
  zipMethod: ZipMethod,
) extends Archiver[Option] {

  override def archive(implicit
    trace: Trace
  ): ZPipeline[Any, Throwable, (ArchiveEntry[Option, Any], ZStream[Any, Throwable, Byte]), Byte] =
    viaOutputStream { outputStream =>
      val zipOutputStream = new ZipOutputStream(outputStream)
      level.foreach(l => zipOutputStream.setLevel(l.level))
      zipOutputStream.setMethod(if (zipMethod == ZipMethod.Deflated) ZipEntry.DEFLATED else ZipEntry.STORED)
      zipOutputStream
    } { case (entryStream, zipOutputStream) =>
      entryStream
        .mapZIO { case (archiveEntry, contentStream) =>
          val entry = archiveEntry.underlying[ZipEntry]
          if (archiveEntry.isDirectory) {
            contentStream.runDrain *>
              ZIO.attemptBlockingInterrupt(zipOutputStream.putNextEntry(entry)) *>
              ZIO.attemptBlockingInterrupt(zipOutputStream.closeEntry())
          } else if (zipMethod == ZipMethod.Stored) {
            entry.setMethod(ZipEntry.STORED)
            // Slurp all content in memory to calculate size and CRC32.
            for {
              contentChunks <- contentStream.chunks.map(_.toArray).runCollect
              _ = {
                val crc = new CRC32()
                contentChunks.foreach(chunk => crc.update(chunk))
                entry.setCrc(crc.getValue)
                val uncompressedSize = contentChunks.map(_.length.toLong).sum
                entry.setSize(uncompressedSize)
                entry.setCompressedSize(uncompressedSize)
              }
              _ <- ZIO.attemptBlockingInterrupt {
                     zipOutputStream.putNextEntry(entry)
                     contentChunks.foreach(zipOutputStream.write)
                     zipOutputStream.closeEntry()
                   }
            } yield ()
          } else {
            // Deflated non-directory entry
            entry.setMethod(ZipEntry.DEFLATED)
            ZIO.attemptBlockingInterrupt(zipOutputStream.putNextEntry(entry)) *>
              contentStream.runForeachChunk { chunk =>
                ZIO.attemptBlockingInterrupt(zipOutputStream.write(chunk.toArray))
              } *>
              ZIO.attemptBlockingInterrupt(zipOutputStream.closeEntry())
          }
        }
        .runDrain
    }
}

/** Constructors for [[ZipUnarchiver]], and `list` and `unarchive` for the default options. */
object ZipUnarchiver {

  /** An [[Unarchiver]] for ZIP, based on the JVM standard library.
    *
    * The archive entries might have the uncompressed size.
    *
    * @param chunkSize
    *   chunkSize of the archive entry content streams. Defaults to 64KiB.
    */
  def apply(chunkSize: Int = Defaults.DefaultChunkSize): ZipUnarchiver =
    new ZipUnarchiver(chunkSize)

  /** See [[apply]] and [[Unarchiver.list]]. */
  def list: ZPipeline[Any, Throwable, Byte, ArchiveEntry[Option, ZipEntry]] =
    apply().list

  /** See [[apply]] and [[Unarchiver.unarchive]].
    *
    * ⚠️Note: the entry's content must be fully read before the next archive entry is emitted. Until then the pipeline
    * waits: when a content stream is not read, or read only in part, it waits forever.
    */
  def unarchive: ZPipeline[Any, Throwable, Byte, (ArchiveEntry[Option, ZipEntry], ZStream[Any, IOException, Byte])] =
    apply().unarchive

}

/** An [[Unarchiver]] that reads ZIP archives with the JVM standard library (`java.util.zip.ZipInputStream`).
  *
  * It reads the archive as a stream, front to back, and emits each entry with its content stream; the content of an
  * entry must be read to its end before the next entry is emitted. The underlying entry is a `java.util.zip.ZipEntry`
  * (read it with `entry.underlying[ZipEntry]` after `import zio.compress.Zip._`). The uncompressed size is `None` for
  * entries whose local header does not record it, which is the case for the deflated file entries that `ZipArchiver`
  * writes; use `list` to get counted sizes. Input that is not a ZIP archive gives an empty stream, not an error. The
  * content streams fail with `java.io.IOException`.
  *
  * {{{
  * import zio.compress._
  * import zio.stream._
  *
  * def lineCounts(zipBytes: ZStream[Any, Throwable, Byte]): ZStream[Any, Throwable, (String, Long)] =
  *   zipBytes
  *     .via(ZipUnarchiver().unarchive)
  *     .mapZIO { case (entry, content) =>
  *       content.via(ZPipeline.utf8Decode >>> ZPipeline.splitLines).runCount.map(lines => entry.name -> lines)
  *     }
  * }}}
  *
  * @see
  *   [[https://zio.dev/zio-streams-compress/reference-zip/ ZIO Streams Compress: zip reference]]
  */
final class ZipUnarchiver private (chunkSize: Int) extends Unarchiver[Option, ZipEntry] {

  override def unarchive(implicit
    trace: Trace
  ): ZPipeline[Any, Throwable, Byte, (ArchiveEntry[Option, ZipEntry], ZStream[Any, IOException, Byte])] =
    viaInputStream[(ArchiveEntry[Option, ZipEntry], ZStream[Any, IOException, Byte])]() { inputStream =>
      for {
        zipInputStream <- ZIO.acquireRelease(ZIO.attemptBlockingInterrupt(new ZipInputStream(inputStream))) {
                            zipInputStream =>
                              ZIO.attemptBlockingInterrupt(zipInputStream.close()).orDie
                          }
      } yield
        ZStream.unfoldZIO(Option.empty[Promise[Nothing, Unit]]) { previousEntryFullyRead =>
          for {
            // Wait with reading the next entry until the contents of the previous entry were fully read
            _ <- previousEntryFullyRead.fold(ZIO.unit)(_.await)
            entryFullyRead <- Promise.make[Nothing, Unit]
            optionalEntry <- ZIO.attemptBlockingInterrupt(Option(zipInputStream.getNextEntry))
          } yield
            optionalEntry.map { entry =>
              val archiveEntry = ArchiveEntry.fromUnderlying[Option, ZipEntry](entry)
              // ZipInputStream.read seems to do its best to read the requested number of bytes. No buffering
              // is needed.
              val entryContentStream = ZStream.fromInputStream(zipInputStream, chunkSize) ++
                ZStream.execute(entryFullyRead.succeed(()))
              ((archiveEntry, entryContentStream), Some(entryFullyRead))
            }
        }
    }

}

/** The conversions between [[ArchiveEntry]] and `java.util.zip.ZipEntry`. Import `zio.compress.Zip._` to call
  * `entry.underlying[ZipEntry]`.
  */
object Zip {

  /** Converts an [[ArchiveEntry]] into a new `java.util.zip.ZipEntry`, for `ZipArchiver` and `ArchiveEntry#underlying`.
    *
    * It copies the underlying `ZipEntry` when its name and directory flag match the `ArchiveEntry`; otherwise, or when
    * there is none, it starts from a new `ZipEntry`, adding a trailing `/` to a directory name and removing it from a
    * file name. A directory gets method stored, size 0 and CRC 0; a file gets the `uncompressedSize`, when set. The
    * `lastModified`, `lastAccess` and `creation` times are set when present. Throws an [[IllegalUnderlyingEntry]] when
    * the underlying value is neither a `ZipEntry` nor absent.
    */
  implicit val zipArchiveEntryToUnderlying: ArchiveEntryToUnderlying[ZipEntry] =
    new ArchiveEntryToUnderlying[ZipEntry] {
      override def underlying[S[A] <: Option[A]](entry: ArchiveEntry[S, Any], underlying: Any): ZipEntry = {
        val zipEntry = underlying match {
          case zipEntry: ZipEntry if zipEntry.getName == entry.name && zipEntry.isDirectory == entry.isDirectory =>
            new ZipEntry(zipEntry)

          // The underlying information is lost if the name or isDirectory attribute of an ArchiveEntry is changed
          case _: ZipEntry | () =>
            val fileOrDirName = entry.name match {
              case name if entry.isDirectory && !name.endsWith("/") => name + "/"
              case name if !entry.isDirectory && name.endsWith("/") => name.dropRight(1)
              case name                                             => name
            }
            new ZipEntry(fileOrDirName)

          case u => throw IllegalUnderlyingEntry(u)
        }

        if (entry.isDirectory) {
          zipEntry.setMethod(ZipEntry.STORED)
          zipEntry.setSize(0)
          zipEntry.setCrc(0)
        } else {
          entry.uncompressedSize.foreach(zipEntry.setSize)
        }

        entry.lastModified.map(FileTime.from).foreach(zipEntry.setLastModifiedTime)
        entry.lastAccess.map(FileTime.from).foreach(zipEntry.setLastAccessTime)
        entry.creation.map(FileTime.from).foreach(zipEntry.setCreationTime)
        zipEntry
      }
    }

  // noinspection ConvertExpressionToSAM
  /** Builds an [[ArchiveEntry]] from a `java.util.zip.ZipEntry`, for `ZipUnarchiver`. It takes the name, the directory
    * flag and the three timestamps, sets the uncompressed size to `None` when the `ZipEntry` gives -1 (size unknown),
    * and keeps the `ZipEntry` as the underlying entry.
    */
  implicit val zipArchiveEntryFromUnderlying: ArchiveEntryFromUnderlying[Option, ZipEntry] =
    new ArchiveEntryFromUnderlying[Option, ZipEntry] {
      override def archiveEntry(underlying: ZipEntry): ArchiveEntry[Option, ZipEntry] =
        ArchiveEntry(
          name = underlying.getName,
          isDirectory = underlying.isDirectory,
          uncompressedSize = Some(underlying.getSize).filterNot(_ == -1),
          lastModified = Option(underlying.getLastModifiedTime).map(_.toInstant),
          lastAccess = Option(underlying.getLastAccessTime).map(_.toInstant),
          creation = Option(underlying.getCreationTime).map(_.toInstant),
          underlying = underlying,
        )
    }
}
