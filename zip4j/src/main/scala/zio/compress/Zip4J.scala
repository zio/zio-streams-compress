package zio.compress

import net.lingala.zip4j.io.inputstream.ZipInputStream
import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.{LocalFileHeader, ZipParameters}
import zio._
import zio.compress.ArchiveEntry.{ArchiveEntryFromUnderlying, ArchiveEntryToUnderlying}
import zio.compress.Archiver.checkUncompressedSize
import zio.compress.JavaIoInterop._
import zio.compress.zip4j._
import zio.compress.Zip4J._
import zio.stream._

import java.io.IOException
import java.time.Instant

/** Constructors for [[Zip4JArchiver]], and `archive` for the default options. */
object Zip4JArchiver {

  /** An [[Archiver]] for ZIP, based on the Zip4J library.
    *
    * The archive entries require the uncompressed size.
    *
    * NOTE: when password is `Some`, the extremely weak standard ZIP encryption is used. Use
    * `Zip4JArchiver.encrypted(...)` to set another encryption method.
    *
    * @param password
    *   password of the ZIP archive, or `None` if the archive is not password protected.
    * @param zipMethod
    *   zip method, either Deflated (the default) or Stored
    */
  def apply(password: Option[String] = None, zipMethod: ZipMethod = ZipMethod.Deflated): Zip4JArchiver =
    password.fold(new Zip4JArchiver(None, zipMethod))(pw => encrypted(pw, EncryptionMethod.ZipStandard, zipMethod))

  /** An [[Archiver]] for encrypted ZIP, based on the Zip4J library.
    *
    * The archive entries require the uncompressed size. File entries are encrypted; directory entries are not.
    *
    * @param password
    *   password of the ZIP archive
    * @param encryptionMethod
    *   the encryption method
    * @param zipMethod
    *   zip method, either Deflated (the default) or Stored
    */
  def encrypted(
    password: String,
    encryptionMethod: EncryptionMethod,
    zipMethod: ZipMethod = ZipMethod.Deflated,
  ): Zip4JArchiver =
    new Zip4JArchiver(Some((password, encryptionMethod)), zipMethod)

  /** See [[apply]] and [[Archiver.archive]]. */
  def archive: ZPipeline[Any, Throwable, (ArchiveEntry[Some, Any], ZStream[Any, Throwable, Byte]), Byte] =
    apply().archive
}

/** An [[Archiver]] that writes ZIP archives with the Zip4J library, optionally password-protected.
  *
  * It takes `ArchiveEntry[Some, _]`: every entry needs its uncompressed size, and when the content has a different
  * number of bytes the archive stream dies with a `java.lang.IllegalStateException`. It writes the name, the size and
  * the `lastModified` time of each entry, not `lastAccess` or `creation`. Directory entries are written stored and
  * unencrypted. `ZipUnarchiver` cannot read the stored file entries it writes (it fails with
  * `java.util.zip.ZipException: only DEFLATED entries can have EXT descriptor`), while `Zip4JUnarchiver` can. Make one
  * with `Zip4JArchiver.apply` or `Zip4JArchiver.encrypted`.
  */
final class Zip4JArchiver private (encryption: Option[(String, EncryptionMethod)], zipMethod: ZipMethod)
    extends Archiver[Some] {

  private[this] val compressionMethod = zipMethod match {
    case ZipMethod.Deflated => CompressionMethod.DEFLATE
    case ZipMethod.Stored   => CompressionMethod.STORE
  }

  /** Password as char array or null. */
  private[this] val passwordOrNull: Array[Char] = encryption.map(_._1.toCharArray).orNull

  /** An optional tuple with zip4j's encryption method and key strength. */
  private[this] val encryptionInfo = encryption
    .map { case (_, encryptionMethod) =>
      import EncryptionMethod._
      import net.lingala.zip4j.model.enums.AesKeyStrength._
      import net.lingala.zip4j.model.enums.EncryptionMethod._
      encryptionMethod match {
        case ZipStandard => (ZIP_STANDARD, None)
        case AES_128     => (AES, Some(KEY_STRENGTH_128))
        case AES_256     => (AES, Some(KEY_STRENGTH_256))
      }
    }

  override def archive(implicit
    trace: Trace
  ): ZPipeline[Any, Throwable, (ArchiveEntry[Some, Any], ZStream[Any, Throwable, Byte]), Byte] =
    viaOutputStream { outputStream =>
      new ZipOutputStream(outputStream, passwordOrNull)
    } { case (entryStream, zipOutputStream) =>
      entryStream
        .via(checkUncompressedSize)
        .mapZIO { case (archiveEntry, contentStream) =>
          val entry = {
            val zp = archiveEntry.underlying[ZipParameters]
            if (archiveEntry.isDirectory) {
              zp.setCompressionMethod(CompressionMethod.STORE)
            } else {
              zp.setCompressionMethod(compressionMethod)
              encryptionInfo.foreach { case (em, ks) =>
                zp.setEncryptFiles(true)
                zp.setEncryptionMethod(em)
                ks.foreach(zp.setAesKeyStrength)
              }
            }
            zp
          }
          ZIO.attemptBlockingInterrupt(zipOutputStream.putNextEntry(entry)) *>
            contentStream.runForeachChunk { chunk =>
              ZIO.attemptBlockingInterrupt(zipOutputStream.write(chunk.toArray))
            } *>
            ZIO.attemptBlockingInterrupt(zipOutputStream.closeEntry())
        }
        .runDrain
    }
}

/** Constructors for [[Zip4JUnarchiver]], and `list` and `unarchive` for the default options (no password). */
object Zip4JUnarchiver {

  /** An [[Unarchiver]] for ZIP, based on the Zip4J library.
    *
    * The archive entries might have the uncompressed size.
    *
    * @param password
    *   password of the ZIP archive, or `None` if the archive is not password protected
    * @param chunkSize
    *   chunkSize of the archive entry content streams. Defaults to 64KiB.
    */
  def apply(
    password: Option[String] = None,
    chunkSize: Int = Defaults.DefaultChunkSize,
  ): Zip4JUnarchiver =
    new Zip4JUnarchiver(password, chunkSize)

  /** See [[apply]] and [[Unarchiver.list]]. */
  def list: ZPipeline[Any, Throwable, Byte, ArchiveEntry[Option, LocalFileHeader]] =
    apply().list

  /** See [[apply]] and [[Unarchiver.unarchive]].
    *
    * ⚠️Note: the entry's content must be fully read before the next archive entry is emitted. Until then the pipeline
    * waits: when a content stream is not read, or read only in part, it waits forever.
    */
  def unarchive
    : ZPipeline[Any, Throwable, Byte, (ArchiveEntry[Option, LocalFileHeader], ZStream[Any, IOException, Byte])] =
    apply().unarchive

}

/** An [[Unarchiver]] that reads ZIP archives with the Zip4J library, including entries encrypted with the ZIP standard
  * encryption or AES when it has the password.
  *
  * It reads the archive as a stream, front to back, and emits each entry with its content stream; the content of an
  * entry must be read to its end before the next entry is emitted. A wrong password fails the stream with a
  * `net.lingala.zip4j.exception.ZipException`, with the message `Wrong password!` for ZIP standard encryption and
  * `Wrong Password` for AES. The underlying entry is Zip4J's `LocalFileHeader`. The entries carry what the local header
  * records:
  *
  *   - The uncompressed size is `Some(0)` for entries whose local header leaves the size to a data descriptor, such as
  *     the deflated file entries that `ZipArchiver` writes. Use `list` to get counted sizes.
  *   - The `lastModified` time is the header's packed ZIP date-time field read as milliseconds since the epoch, so it
  *     falls in early 1970 instead of the time the archive records.
  */
final class Zip4JUnarchiver private (password: Option[String], chunkSize: Int)
    extends Unarchiver[Option, LocalFileHeader] {

  override def unarchive(implicit
    trace: Trace
  ): ZPipeline[Any, Throwable, Byte, (ArchiveEntry[Option, LocalFileHeader], ZStream[Any, IOException, Byte])] =
    viaInputStream[(ArchiveEntry[Option, LocalFileHeader], ZStream[Any, IOException, Byte])]() { inputStream =>
      for {
        zipInputStream <-
          ZIO.acquireRelease(
            ZIO.attemptBlockingInterrupt(new ZipInputStream(inputStream, password.map(_.toCharArray).orNull))
          ) { zipInputStream =>
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
              val archiveEntry = ArchiveEntry.fromUnderlying[Option, LocalFileHeader](entry)
              // ZipInputStream.read seems to do its best to read the requested number of bytes. No buffering
              // is needed.
              val entryContentStream = ZStream.fromInputStream(zipInputStream, chunkSize) ++
                ZStream.execute(entryFullyRead.succeed(()))
              ((archiveEntry, entryContentStream), Some(entryFullyRead))
            }
        }
    }

}

/** The conversions between [[ArchiveEntry]] and the Zip4J types: `ZipParameters` for writing and `LocalFileHeader` for
  * reading. Import `zio.compress.Zip4J._` to call `entry.underlying[ZipParameters]` or
  * `entry.underlying[LocalFileHeader]`.
  */
object Zip4J {

  /** Converts an [[ArchiveEntry]] into Zip4J `ZipParameters`, for `Zip4JArchiver` and `ArchiveEntry#underlying`.
    *
    * It copies the underlying `ZipParameters`, or starts from new ones when there is none, and then sets the file name
    * (with a trailing `/` for a directory, without one for a file), the entry size when set, and the last-modified time
    * when set. Throws an [[IllegalUnderlyingEntry]] when the underlying value is neither `ZipParameters` nor absent.
    */
  implicit val zip4jArchiveEntryToUnderlying: ArchiveEntryToUnderlying[ZipParameters] =
    new ArchiveEntryToUnderlying[ZipParameters] {
      override def underlying[S[A] <: Option[A]](entry: ArchiveEntry[S, Any], underlying: Any): ZipParameters = {
        val zipEntry = underlying match {
          case zipParameters: ZipParameters => new ZipParameters(zipParameters)
          case ()                           => new ZipParameters()
          case u                            => throw IllegalUnderlyingEntry(u)
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

  /** Returns the underlying `LocalFileHeader` of an [[ArchiveEntry]] itself, not a copy, or an empty `LocalFileHeader`
    * when the entry has none. Used by `ArchiveEntry#underlying` on the entries of `Zip4JUnarchiver`. Throws an
    * [[IllegalUnderlyingEntry]] when the underlying value is neither a `LocalFileHeader` nor absent.
    */
  implicit val zip4jUnarchiveEntryToUnderlying: ArchiveEntryToUnderlying[LocalFileHeader] =
    new ArchiveEntryToUnderlying[LocalFileHeader] {
      override def underlying[S[A] <: Option[A]](entry: ArchiveEntry[S, Any], underlying: Any): LocalFileHeader =
        underlying match {
          case localFileHeader: LocalFileHeader => localFileHeader
          case ()                               => new LocalFileHeader()
          case u                                => throw IllegalUnderlyingEntry(u)
        }
    }

  // noinspection ConvertExpressionToSAM
  /** Builds an [[ArchiveEntry]] from Zip4J `ZipParameters`, keeping them as the underlying entry. The name is the file
    * name in the ZIP, the entry is a directory when that name ends with `/`, the size is `None` when the parameters
    * give -1, and `lastModified` is the parameters' last-modified time (the epoch, `1970-01-01T00:00:00Z`, when it was
    * never set).
    */
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
  /** Builds an [[ArchiveEntry]] from a Zip4J `LocalFileHeader`, for `Zip4JUnarchiver`, keeping the header as the
    * underlying entry. It takes the file name and the directory flag, sets the size to `None` when the header gives -1,
    * and reads the header's packed ZIP date-time field as milliseconds since the epoch for `lastModified`, which gives
    * a time in early 1970.
    */
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
