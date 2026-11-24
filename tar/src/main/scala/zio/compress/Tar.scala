package zio.compress

import org.apache.commons.compress.archivers.tar.{TarArchiveEntry, TarArchiveInputStream, TarArchiveOutputStream}
import org.apache.commons.compress.archivers.{ArchiveEntry => CommonsArchiveEntry}
import zio._
import zio.compress.ArchiveEntry._
import zio.compress.Archiver.checkUncompressedSize
import zio.compress.JavaIoInterop._
import zio.compress.Tar._
import zio.stream._

import java.io.{BufferedInputStream, IOException}
import java.nio.file.attribute.FileTime

/** Constructor for [[TarArchiver]], and `archive` as a shortcut. */
object TarArchiver {

  /** An [[Archiver]] for Tar, based on the Apache Commons Compress library.
    *
    * The archive entries require the uncompressed size.
    */
  def apply(): TarArchiver =
    new TarArchiver()

  /** See [[apply]] and [[Archiver.archive]]. */
  def archive: ZPipeline[Any, Throwable, (ArchiveEntry[Some, Any], ZStream[Any, Throwable, Byte]), Byte] =
    apply().archive
}

/** An [[Archiver]] that writes Tar archives with Apache Commons Compress (`TarArchiveOutputStream`).
  *
  * A Tar archive bundles files without compressing them; pipe the archive through a [[Compressor]], such as
  * `GzipCompressor`, for a compressed tarball. Every entry needs its uncompressed size, so the input is
  * `ArchiveEntry[Some, _]`. When the content is longer than the declared size, the stream fails with a
  * `java.io.IOException` from Commons Compress; when it is shorter, the stream dies with a
  * `java.lang.IllegalStateException`. Give a directory entry size 0 and an empty content stream; its name gets a
  * trailing `/`. Of the timestamps, only `lastModified` comes back when `TarUnarchiver` reads the archive; `lastAccess`
  * and `creation` come back as `None`. To write Tar-specific fields, such as the Unix permissions, attach a
  * commons-compress `TarArchiveEntry` with `ArchiveEntry#withUnderlying`.
  *
  * {{{
  * import zio.compress._
  * import zio.stream._
  *
  * val chapter = "It is a truth universally acknowledged".getBytes("UTF-8")
  *
  * val tarBytes: ZStream[Any, Throwable, Byte] =
  *   ZStream(
  *     ArchiveEntry[Option, Any]("novels", isDirectory = true).withKnownUncompressedSize(0L) -> ZStream.empty,
  *     ArchiveEntry[Option, Any]("novels/pride.txt").withKnownUncompressedSize(chapter.length.toLong) ->
  *       ZStream.fromIterable(chapter),
  *   ).via(TarArchiver().archive)
  * }}}
  *
  * @see
  *   [[https://zio.dev/zio-streams-compress/reference-tar/ ZIO Streams Compress: tar reference]]
  */
final class TarArchiver private extends Archiver[Some] {

  override def archive(implicit
    trace: Trace
  ): ZPipeline[Any, Throwable, (ArchiveEntry[Some, Any], ZStream[Any, Throwable, Byte]), Byte] =
    viaOutputStream(new TarArchiveOutputStream(_)) { case (entryStream, tarOutputStream) =>
      entryStream
        .via(checkUncompressedSize)
        .mapZIO { case (archiveEntry, contentStream) =>
          val entry = archiveEntry.underlying[TarArchiveEntry]
          ZIO.attemptBlockingInterrupt(tarOutputStream.putArchiveEntry(entry)) *>
            contentStream.runForeachChunk { chunk =>
              ZIO.attemptBlockingInterrupt(tarOutputStream.write(chunk.toArray))
            } *>
            ZIO.attemptBlockingInterrupt(tarOutputStream.closeArchiveEntry())
        }
        .runDrain
    }
}

/** Constructors for [[TarUnarchiver]], and `list` and `unarchive` for the default options. */
object TarUnarchiver {

  /** An [[Unarchiver]] for Tar, based on the Apache Commons Compress library.
    *
    * The archive entries might have a known uncompressed size.
    *
    * @param chunkSize
    *   chunkSize of the archive entry content streams. Defaults to 64KiB.
    */
  def apply(chunkSize: Int = Defaults.DefaultChunkSize): TarUnarchiver =
    new TarUnarchiver(chunkSize)

  /** See [[apply]] and [[Unarchiver.list]]. */
  def list: ZPipeline[Any, Throwable, Byte, ArchiveEntry[Option, TarArchiveEntry]] =
    apply().list

  /** See [[apply]] and [[Unarchiver.unarchive]].
    *
    * ⚠️Note: the entry's content must be fully read before the next archive entry is emitted. Until then the pipeline
    * waits: when a content stream is not read, or read only in part, it waits forever.
    */
  def unarchive
    : ZPipeline[Any, Throwable, Byte, (ArchiveEntry[Option, TarArchiveEntry], ZStream[Any, IOException, Byte])] =
    apply().unarchive

}

/** An [[Unarchiver]] that reads Tar archives with Apache Commons Compress (`TarArchiveInputStream`).
  *
  * It reads the archive as a stream, front to back, and emits each entry with its content stream; the content of an
  * entry must be read to its end before the next entry is emitted. For a compressed tarball, decompress first, for
  * example with `GzipDecompressor`. Each entry has the name, the directory flag, the size and the timestamps the
  * archive records (for archives written by `TarArchiver`, only `lastModified`). The underlying entry is a
  * commons-compress `TarArchiveEntry`, which also holds the Unix permissions, the owner and the group; read it with
  * `entry.underlying[TarArchiveEntry]` after `import zio.compress.Tar._`. Input that is not a Tar archive can give an
  * empty stream, as ZIP bytes do, or fail with a `java.io.IOException` (`Corrupted TAR archive.`), as plain text does.
  * The content streams fail with `java.io.IOException`.
  *
  * {{{
  * import zio.compress._
  * import zio.compress.Tar._
  * import zio.stream._
  * import org.apache.commons.compress.archivers.tar.TarArchiveEntry
  *
  * def permissions(tarBytes: ZStream[Any, Throwable, Byte]): ZStream[Any, Throwable, (String, String)] =
  *   tarBytes
  *     .via(TarUnarchiver().list)
  *     .map(entry => entry.name -> Integer.toOctalString(entry.underlying[TarArchiveEntry].getMode))
  * }}}
  *
  * @see
  *   [[https://zio.dev/zio-streams-compress/reference-tar/ ZIO Streams Compress: tar reference]]
  */
final class TarUnarchiver private (chunkSize: Int) extends Unarchiver[Option, TarArchiveEntry] {

  override def unarchive(implicit
    trace: Trace
  ): ZPipeline[Any, Throwable, Byte, (ArchiveEntry[Option, TarArchiveEntry], ZStream[Any, IOException, Byte])] =
    viaInputStream[(ArchiveEntry[Option, TarArchiveEntry], ZStream[Any, IOException, Byte])]() { inputStream =>
      for {
        tarInputStream <-
          ZIO.acquireRelease(ZIO.attemptBlockingInterrupt(new TarArchiveInputStream(inputStream))) { tarInputStream =>
            ZIO.attemptBlockingInterrupt(tarInputStream.close()).orDie
          }
      } yield
        ZStream.unfoldZIO(Option.empty[Promise[Nothing, Unit]]) { previousEntryFullyRead =>
          for {
            // Wait with reading the next entry until the contents of the previous entry were fully read
            _ <- previousEntryFullyRead.fold(ZIO.unit)(_.await)
            entryFullyRead <- Promise.make[Nothing, Unit]
            optionalEntry <- ZIO.attemptBlockingInterrupt(Option(tarInputStream.getNextEntry))
          } yield
            optionalEntry.map { entry =>
              val archiveEntry = ArchiveEntry.fromUnderlying[Option, TarArchiveEntry](entry)
              // TarArchiveInputStream.read does not try to read the requested number of bytes, but it does have a good
              // `available()` implementation, so with buffering we can still get full chunks.
              val entryContentStream =
                ZStream.fromInputStream(new BufferedInputStream(tarInputStream, chunkSize), chunkSize) ++
                  ZStream.execute(entryFullyRead.succeed(()))
              ((archiveEntry, entryContentStream), Some(entryFullyRead))
            }
        }
    }
}

/** The conversions between [[ArchiveEntry]] and the commons-compress `TarArchiveEntry`. Import `zio.compress.Tar._` to
  * call `entry.underlying[TarArchiveEntry]`.
  */
object Tar {

  /** Converts an [[ArchiveEntry]] into a new commons-compress `TarArchiveEntry`, for `TarArchiver` and
    * `ArchiveEntry#underlying`.
    *
    * The name gets a trailing `/` for a directory and loses it for a file. When the underlying `TarArchiveEntry` has
    * the same directory flag as the `ArchiveEntry`, it copies that entry's header, so fields such as the permissions
    * and the owner are kept, and sets the name; otherwise, or when there is none, it starts from a new
    * `TarArchiveEntry`. It then sets the size and the `lastModified`, `lastAccess` and `creation` times that are
    * present. Throws an [[IllegalUnderlyingEntry]] when the underlying value is neither a `TarArchiveEntry` nor absent.
    */
  implicit val tarArchiveEntryToUnderlying: ArchiveEntryToUnderlying[TarArchiveEntry] =
    new ArchiveEntryToUnderlying[TarArchiveEntry] {
      override def underlying[S[A] <: Option[A]](entry: ArchiveEntry[S, Any], underlying: Any): TarArchiveEntry = {
        val fileOrDirName = entry.name match {
          case name if entry.isDirectory && !name.endsWith("/") => name + "/"
          case name if !entry.isDirectory && name.endsWith("/") => name.dropRight(1)
          case name                                             => name
        }

        val tarEntry = underlying match {
          case tarEntry: TarArchiveEntry if tarEntry.isDirectory == entry.isDirectory =>
            // copy TarArchiveEntry
            val buffer = new Array[Byte](512) // TarArchiveOutputStream.RECORD_SIZE
            tarEntry.writeEntryHeader(buffer)
            val newTarEntry = new TarArchiveEntry(buffer)
            newTarEntry.setName(fileOrDirName)
            newTarEntry

          // The underlying information is lost if the isDirectory attribute of an ArchiveEntry is changed
          case _: TarArchiveEntry | () =>
            new TarArchiveEntry(fileOrDirName)

          case u => throw IllegalUnderlyingEntry(u)
        }

        entry.uncompressedSize.foreach(tarEntry.setSize)
        entry.lastModified.map(FileTime.from).foreach(tarEntry.setLastModifiedTime)
        entry.lastAccess.map(FileTime.from).foreach(tarEntry.setLastAccessTime)
        entry.creation.map(FileTime.from).foreach(tarEntry.setCreationTime)
        tarEntry
      }
    }

  // noinspection ConvertExpressionToSAM
  /** Builds an [[ArchiveEntry]] from a commons-compress `TarArchiveEntry`, for `TarUnarchiver`, keeping it as the
    * underlying entry. It takes the name, the directory flag, the size (`None` when the entry gives -1, the
    * commons-compress `SIZE_UNKNOWN`) and the three timestamps that are present.
    */
  implicit val tarArchiveEntryFromUnderlying: ArchiveEntryFromUnderlying[Option, TarArchiveEntry] =
    new ArchiveEntryFromUnderlying[Option, TarArchiveEntry] {
      override def archiveEntry(underlying: TarArchiveEntry): ArchiveEntry[Option, TarArchiveEntry] =
        ArchiveEntry(
          name = underlying.getName,
          isDirectory = underlying.isDirectory,
          uncompressedSize = Some(underlying.getSize).filterNot(_ == CommonsArchiveEntry.SIZE_UNKNOWN),
          lastModified = Option(underlying.getLastModifiedTime).map(_.toInstant),
          lastAccess = Option(underlying.getLastAccessTime).map(_.toInstant),
          creation = Option(underlying.getCreationTime).map(_.toInstant),
          underlying = underlying,
        )
    }
}
