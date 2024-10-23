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
          ZIO.attemptBlocking(zipOutputStream.putNextEntry(entry)) *>
            contentStream.runForeachChunk(chunk => ZIO.attemptBlocking(zipOutputStream.write(chunk.toArray))) *>
            ZIO.attemptBlocking(zipOutputStream.closeEntry())
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

  /** See [[apply]] and [[Unarchiver.unarchive]]. */
  def unarchive: ZPipeline[Any, Throwable, Byte, (ArchiveEntry[Option, ZipEntry], ZStream[Any, IOException, Byte])] =
    apply().unarchive
}

final class ZipUnarchiver private (chunkSize: Int) extends Unarchiver[Option, ZipEntry] {

  override def unarchive(implicit
    trace: Trace
  ): ZPipeline[Any, Throwable, Byte, (ArchiveEntry[Option, ZipEntry], ZStream[Any, IOException, Byte])] =
    viaInputStream[(ArchiveEntry[Option, ZipEntry], ZStream[Any, IOException, Byte])]() { inputStream =>
      for {
        zipInputStream <- ZIO.acquireRelease(ZIO.attemptBlocking(new ZipInputStream(inputStream))) { zipInputStream =>
                            ZIO.attemptBlocking(zipInputStream.close()).orDie
                          }
      } yield
        ZStream.repeatZIOOption {
          for {
            entry <- ZIO.attemptBlocking(Option(zipInputStream.getNextEntry)).some
          } yield {
            val archiveEntry = ArchiveEntry.fromUnderlying(entry)
            // ZipInputStream.read seems to do its best to read to request the requested number of bytes. No buffering
            // is needed.
            (archiveEntry, ZStream.fromInputStream(zipInputStream, chunkSize))
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
