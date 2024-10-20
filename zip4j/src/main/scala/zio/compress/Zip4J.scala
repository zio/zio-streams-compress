package zio.compress

import net.lingala.zip4j.io.inputstream.ZipInputStream
import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.{LocalFileHeader, ZipParameters}
import zio._
import zio.compress.ArchiveEntry.{ArchiveEntryFromUnderlying, ArchiveEntryToUnderlying}
import zio.compress.Archiver.checkUncompressedSize
import zio.compress.JavaIoInterop._
import zio.compress.Zip4J._
import zio.stream._

import java.io.IOException
import java.time.Instant

object Zip4JArchiver {

  /**
   * Makes a pipeline that accepts a stream of archive entries (with size), and produces a byte stream of a Zip archive
   * (method: deflate, level: 5).
   *
   * @param password
   *   password of the ZIP archive, or `None` if the archive is not password protected
   */
  def make(password: => Option[String] = None): Zip4JArchiver =
    new Zip4JArchiver(password)
}

class Zip4JArchiver private (password: => Option[String]) extends Archiver[Some] {
  override def archive: ZPipeline[Any, Throwable, (ArchiveEntry[Some, Any], ZStream[Any, Throwable, Byte]), Byte] =
    viaOutputStream { outputStream =>
      new ZipOutputStream(outputStream, password.map(_.toCharArray).orNull)
    } { case (entryStream, zipOutputStream) =>
      entryStream
        .via(checkUncompressedSize)
        .mapZIO { case (archiveEntry, contentStream) =>
          def entry = archiveEntry.underlying[ZipParameters]
          ZIO.attemptBlocking(zipOutputStream.putNextEntry(entry)) *>
            contentStream.runForeachChunk(chunk => ZIO.attemptBlocking(zipOutputStream.write(chunk.toArray))) *>
            ZIO.attemptBlocking(zipOutputStream.closeEntry())
        }
        .runDrain
    }
}

object Zip4JUnarchiver {

  /**
   * Makes a pipeline that accepts a byte stream of a ZIP archive, and produces a stream of archive entries.
   *
   * @param password
   *   password of the ZIP archive, or `None` if the archive is not password protected
   * @param chunkSize
   *   chunkSize of the archive entry content streams. Defaults to 64KiB.
   */
  def make(
    password: Option[String] = None,
    chunkSize: Int = Defaults.DefaultChunkSize,
  ): Zip4JUnarchiver =
    new Zip4JUnarchiver(password, chunkSize)
}

class Zip4JUnarchiver private (password: Option[String], chunkSize: Int) extends Unarchiver[Option, LocalFileHeader] {
  override def unarchive
    : ZPipeline[Any, Throwable, Byte, (ArchiveEntry[Option, LocalFileHeader], ZStream[Any, IOException, Byte])] =
    viaInputStream[(ArchiveEntry[Option, LocalFileHeader], ZStream[Any, IOException, Byte])]() { inputStream =>
      for {
        zipInputStream <- ZIO.acquireRelease(
                            ZIO.attemptBlocking(new ZipInputStream(inputStream, password.map(_.toCharArray).orNull))
                          ) { zipInputStream =>
                            ZIO.attemptBlocking(zipInputStream.close()).orDie
                          }
      } yield
        ZStream.repeatZIOOption {
          for {
            entry <- ZIO.attemptBlocking(Option(zipInputStream.getNextEntry)).some
          } yield {
            val archiveEntry = ArchiveEntry.fromUnderlying(entry)
            // ZipInputStream.read seems to do its best to read the requested number of bytes. No buffering is needed.
            (archiveEntry, ZStream.fromInputStream(zipInputStream, chunkSize))
          }
        }
    }
}

object Zip4J {
  implicit val zip4jArchiveEntryToUnderlying: ArchiveEntryToUnderlying[ZipParameters] =
    new ArchiveEntryToUnderlying[ZipParameters] {
      override def underlying[S[A] <: Option[A]](entry: ArchiveEntry[S, Any], underlying: Any): ZipParameters = {
        val zipEntry = underlying match {
          case zipParameters: ZipParameters =>
            new ZipParameters(zipParameters)
          case _ =>
            new ZipParameters()
        }

        val fileOrDirName = entry.name match {
          case name if entry.isDirectory && !name.endsWith("/") => name + "/"
          case name if !entry.isDirectory && name.endsWith("/") => name.dropRight(1)
          case name                                             => name
        }

        zipEntry.setFileNameInZip(fileOrDirName)
        entry.uncompressedSize.foreach(zipEntry.setEntrySize)
        entry.lastModified.map(_.toEpochMilli).foreach(zipEntry.setLastModifiedFileTime)
        zipEntry
      }
    }

  // noinspection ConvertExpressionToSAM
  implicit val zip4jArchiveEntryFromUnderlying: ArchiveEntryFromUnderlying[Option, ZipParameters] =
    new ArchiveEntryFromUnderlying[Option, ZipParameters] {
      override def archiveEntry(underlying: ZipParameters): ArchiveEntry[Option, ZipParameters] =
        ArchiveEntry(
          name = underlying.getFileNameInZip,
          isDirectory = underlying.getFileNameInZip.endsWith("/"), // TODO entry.isDirectory
          uncompressedSize = Some(underlying.getEntrySize).filterNot(_ == -1),
          lastModified = Some(underlying.getLastModifiedFileTime).map(Instant.ofEpochMilli),
          underlying = underlying,
        )
    }

  // noinspection ConvertExpressionToSAM
  implicit val zip4jLocalFileHeaderArchiveEntryFromUnderlying: ArchiveEntryFromUnderlying[Option, LocalFileHeader] =
    new ArchiveEntryFromUnderlying[Option, LocalFileHeader] {
      override def archiveEntry(underlying: LocalFileHeader): ArchiveEntry[Option, LocalFileHeader] =
        ArchiveEntry(
          name = underlying.getFileName,
          isDirectory = underlying.isDirectory,
          uncompressedSize = Some(underlying.getUncompressedSize).filterNot(_ == -1),
          lastModified = Some(underlying.getLastModifiedTime).map(Instant.ofEpochMilli),
          underlying = underlying,
        )
    }
}
