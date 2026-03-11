package zio.compress

import zio._
import zio.compress.ArchiveEntry.{ArchiveEntryFromUnderlying, ArchiveEntryToUnderlying}
import zio.compress.JavaIoInterop._
import zio.compress.Zip._
import zio.stream._

import java.io.IOException
import java.nio.file.attribute.FileTime
import java.util.zip.{ZipEntry, ZipInputStream, ZipOutputStream}

object ZipArchiver {

  /** An [[Archiver]] for ZIP, based on the JVM standard library.
    *
    * The archive entries do not require an uncompressed size.
    *
    * @param level
    *   compression level (only applicable for method 'deflated'). Currently defaults to level 6.
    * @param zipMethod
    *   zip method: stored (no compression) or deflated. Defaults to deflated.
    */
  def apply(
    level: Option[DeflateCompressionLevel] = None,
    zipMethod: Option[ZipMethod] = None,
  ): ZipArchiver =
    new ZipArchiver(level, zipMethod)

  /** See [[apply]] and [[Archiver.archive]]. */
  def archive: ZPipeline[Any, Throwable, (ArchiveEntry[Option, Any], ZStream[Any, Throwable, Byte]), Byte] =
    apply().archive
}

final class ZipArchiver private (
  level: Option[DeflateCompressionLevel],
  zipMethod: Option[ZipMethod],
) extends Archiver[Option] {

  override def archive(implicit
    trace: Trace
  ): ZPipeline[Any, Throwable, (ArchiveEntry[Option, Any], ZStream[Any, Throwable, Byte]), Byte] =
    viaOutputStream { outputStream =>
      val zipOutputStream = new ZipOutputStream(outputStream)
      level.foreach(l => zipOutputStream.setLevel(l.level))
      zipMethod
        .map {
          case ZipMethod.Deflated => ZipEntry.DEFLATED
          case ZipMethod.Stored   => ZipEntry.STORED
        }
        .foreach(zipOutputStream.setMethod)
      zipOutputStream
    } { case (entryStream, zipOutputStream) =>
      entryStream
        .mapZIO { case (archiveEntry, contentStream) =>
          def entry = archiveEntry.underlying[ZipEntry]
          ZIO.attemptBlockingInterrupt(zipOutputStream.putNextEntry(entry)) *>
            contentStream.runForeachChunk { chunk =>
              ZIO.attemptBlockingInterrupt(zipOutputStream.write(chunk.toArray))
            } *>
            ZIO.attemptBlockingInterrupt(zipOutputStream.closeEntry())
        }
        .runDrain
    }
}

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
    * ⚠️Note: the entry's content must be fully read before the next archive entry is emitted. See the project's README
    * for the consequences, tips and workarounds.
    */
  def unarchive: ZPipeline[Any, Throwable, Byte, (ArchiveEntry[Option, ZipEntry], ZStream[Any, IOException, Byte])] =
    apply().unarchive

}

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

object Zip {
  // The underlying information is lost if the name or isDirectory attribute of an ArchiveEntry is changed
  implicit val zipArchiveEntryToUnderlying: ArchiveEntryToUnderlying[ZipEntry] =
    new ArchiveEntryToUnderlying[ZipEntry] {
      override def underlying[S[A] <: Option[A]](entry: ArchiveEntry[S, Any], underlying: Any): ZipEntry = {
        val zipEntry = underlying match {
          case zipEntry: ZipEntry if zipEntry.getName == entry.name && zipEntry.isDirectory == entry.isDirectory =>
            new ZipEntry(zipEntry)

          case _ =>
            val fileOrDirName = entry.name match {
              case name if entry.isDirectory && !name.endsWith("/") => name + "/"
              case name if !entry.isDirectory && name.endsWith("/") => name.dropRight(1)
              case name                                             => name
            }
            new ZipEntry(fileOrDirName)
        }

        entry.uncompressedSize.foreach(zipEntry.setSize)
        entry.lastModified.map(FileTime.from).foreach(zipEntry.setLastModifiedTime)
        entry.lastAccess.map(FileTime.from).foreach(zipEntry.setLastAccessTime)
        entry.creation.map(FileTime.from).foreach(zipEntry.setCreationTime)
        zipEntry
      }
    }

  // noinspection ConvertExpressionToSAM
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
