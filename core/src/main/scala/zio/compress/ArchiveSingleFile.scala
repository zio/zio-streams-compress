package zio.compress

import zio.Trace
import zio.stream._

/** A [[Compressor]] that writes its input as the only entry of an archive, with the archiver and the entry it was made
  * with. Make one with `ArchiveSingleFileCompressor.forName` or `ArchiveSingleFileCompressor.apply`.
  *
  * With an archiver that needs the uncompressed size, the input must have exactly the declared number of bytes;
  * otherwise the stream ends with an error.
  */
final class ArchiveSingleFileCompressor[Size[A] <: Option[A]] private (
  archiver: Archiver[Size],
  entry: ArchiveEntry[Size, Any],
) extends Compressor {

  override def compress(implicit trace: Trace): ZPipeline[Any, Throwable, Byte, Byte] =
    ZPipeline.fromFunction { stream =>
      ZStream((entry, stream)).via(archiver.archive)
    }
}

/** Constructors for [[ArchiveSingleFileCompressor]]. */
object ArchiveSingleFileCompressor {

  /** Makes a compressor that writes its input as the content of `entry`, with `archiver`. Use it to set more than the
    * name and size, for example the last-modified time.
    */
  def apply[Size[A] <: Option[A]](
    archiver: Archiver[Size],
    entry: ArchiveEntry[Size, Any],
  ): ArchiveSingleFileCompressor[Size] =
    new ArchiveSingleFileCompressor(archiver, entry)

  /** Makes a compressor that writes its input as a file named `name`, for an archiver that does not need the
    * uncompressed size, such as `ZipArchiver`.
    */
  def forName(archiver: Archiver[Option], name: String): ArchiveSingleFileCompressor[Option] =
    new ArchiveSingleFileCompressor(archiver, ArchiveEntry(name))

  /** Makes a compressor that writes its input as a file named `name` of `size` bytes, for an archiver that needs the
    * uncompressed size, such as `TarArchiver` or `Zip4JArchiver`. `size` must be the exact number of bytes of the
    * input.
    */
  def forName(archiver: Archiver[Some], name: String, size: Long): ArchiveSingleFileCompressor[Some] =
    new ArchiveSingleFileCompressor(archiver, ArchiveEntry(name, Some(size)))
}

/** A [[Decompressor]] that reads an archive with the unarchiver it was made with, and emits the content of the first
  * entry that is not a directory. It reads through the directory entries before that entry, ignores every entry after
  * it, and drops the entry's name and metadata. An archive without a file entry, such as an empty ZIP archive, gives an
  * empty stream.
  */
final class ArchiveSingleFileDecompressor[Size[A] <: Option[A], Underlying] private (
  unarchiver: Unarchiver[Size, Underlying]
) extends Decompressor {

  override def decompress(implicit trace: Trace): ZPipeline[Any, Throwable, Byte, Byte] =
    ZPipeline.fromFunction { stream =>
      stream
        .via(unarchiver.unarchive)
        .flatMap {
          case (entry, s) if entry.isDirectory => s.drain
          case (_, s)                          => ZStream(s)
        }
        .take(1)
        .flatten
    }
}

/** Constructor for [[ArchiveSingleFileDecompressor]]. */
object ArchiveSingleFileDecompressor {

  /** Makes a decompressor that emits the content of the first file entry of an archive read with `unarchiver`. */
  def apply[Size[A] <: Option[A], Underlying](
    unarchiver: Unarchiver[Size, Underlying]
  ): ArchiveSingleFileDecompressor[Size, Underlying] =
    new ArchiveSingleFileDecompressor(unarchiver)
}
