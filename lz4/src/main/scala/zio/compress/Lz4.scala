package zio.compress

import net.jpountz.lz4.LZ4FrameOutputStream.BLOCKSIZE
import net.jpountz.lz4.{LZ4FrameInputStream, LZ4FrameOutputStream}
import zio.compress.JavaIoInterop.{viaInputStreamByte, viaOutputStreamByte}
import zio.stream._

import java.io.BufferedInputStream

sealed trait Lz4CompressorBlockSize
object Lz4CompressorBlockSize {
  case object BlockSize64KiB extends Lz4CompressorBlockSize
  case object BlockSize256KiB extends Lz4CompressorBlockSize
  case object BlockSize1MiB extends Lz4CompressorBlockSize
  case object BlockSize4MiB extends Lz4CompressorBlockSize

  /**
   * Converts a Lz4 block size indicator into a [[Lz4CompressorBlockSize]].
   *
   * @param indicator
   *   the Lz4 block size indicator, valid values: 4 (64KiB), 5 (256KiB), 6 (1MiB), 7 (4MiB)
   */
  def fromLz4BlockSizeIndicator(indicator: Int): Option[Lz4CompressorBlockSize] =
    indicator match {
      case 4 => Some(BlockSize64KiB)
      case 5 => Some(BlockSize256KiB)
      case 6 => Some(BlockSize1MiB)
      case 7 => Some(BlockSize4MiB)
      case _ => None
    }
}

object Lz4Compressor {

  /**
   * Make a pipeline that accepts a stream of bytes and produces a stream with Lz4 compressed bytes.
   *
   * @param blockSize
   *   the block size to use. Defaults to 256KiB.
   */
  def make(
    blockSize: Lz4CompressorBlockSize = Lz4CompressorBlockSize.BlockSize256KiB
  ): Lz4Compressor = {
    val lz4BlockSize = blockSize match {
      case Lz4CompressorBlockSize.BlockSize64KiB  => BLOCKSIZE.SIZE_64KB
      case Lz4CompressorBlockSize.BlockSize256KiB => BLOCKSIZE.SIZE_256KB
      case Lz4CompressorBlockSize.BlockSize1MiB   => BLOCKSIZE.SIZE_1MB
      case Lz4CompressorBlockSize.BlockSize4MiB   => BLOCKSIZE.SIZE_4MB
    }
    new Lz4Compressor(lz4BlockSize)
  }
}

class Lz4Compressor private (blockSize: LZ4FrameOutputStream.BLOCKSIZE) extends Compressor {
  override def compress: ZPipeline[Any, Throwable, Byte, Byte] =
    viaOutputStreamByte(new LZ4FrameOutputStream(_, blockSize))
}

object Lz4Decompressor {

  /**
   * Makes a pipeline that accepts a Lz4 compressed byte stream and produces a decompressed byte stream.
   *
   * @param chunkSize
   *   The maximum chunk size of the outgoing ZStream. Defaults to `ZStream.DefaultChunkSize` (4KiB).
   */
  def make(chunkSize: Int = ZStream.DefaultChunkSize): Lz4Decompressor =
    new Lz4Decompressor(chunkSize)
}

class Lz4Decompressor private (chunkSize: Int) extends Decompressor {
  override def decompress: ZPipeline[Any, Throwable, Byte, Byte] =
    // LZ4FrameInputStream.read does not try to read the requested number of bytes, but it does have a good
    // `available()` implementation, so with buffering we can still get full chunks.
    viaInputStreamByte(chunkSize) { inputStream =>
      new BufferedInputStream(new LZ4FrameInputStream(inputStream), chunkSize)
    }
}
