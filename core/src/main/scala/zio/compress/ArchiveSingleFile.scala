package zio.compress

import zio.Trace
import zio.stream._

final class ArchiveSingleFileCompressor[Size[A] <: Option[A]] private (
  archiver: Archiver[Size],
  entry: ArchiveEntry[Size, Any],
) extends Compressor {

  /** @inheritdoc */
  override def compress(implicit trace: Trace): ZPipeline[Any, Throwable, Byte, Byte] =
    ZPipeline.fromFunction { stream =>
      ZStream((entry, stream)).via(archiver.archive)
    }
}

object ArchiveSingleFileCompressor {
  def apply[Size[A] <: Option[A]](
    archiver: Archiver[Size],
    entry: ArchiveEntry[Size, Any],
  ): ArchiveSingleFileCompressor[Size] =
    new ArchiveSingleFileCompressor(archiver, entry)

  def forName(archiver: Archiver[Option], name: String): ArchiveSingleFileCompressor[Option] =
    new ArchiveSingleFileCompressor(archiver, ArchiveEntry(name))

  def forName(archiver: Archiver[Some], name: String, size: Long): ArchiveSingleFileCompressor[Some] =
    new ArchiveSingleFileCompressor(archiver, ArchiveEntry(name, Some(size)))
}

final class ArchiveSingleFileDecompressor[Size[A] <: Option[A], Underlying] private (
  unarchiver: Unarchiver[Size, Underlying]
) extends Decompressor {

  /** @inheritdoc */
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

object ArchiveSingleFileDecompressor {
  def apply[Size[A] <: Option[A], Underlying](
    unarchiver: Unarchiver[Size, Underlying]
  ): ArchiveSingleFileDecompressor[Size, Underlying] =
    new ArchiveSingleFileDecompressor(unarchiver)
}
