package zio.compress

import net.jpountz.lz4.LZ4FrameOutputStream.BLOCKSIZE
import net.jpountz.lz4.{LZ4FrameInputStream, LZ4FrameOutputStream}
import zio.Trace
import zio.compress.JavaIoInterop.{viaInputStreamByte, viaOutputStreamByte}
import zio.stream._

import java.io.BufferedInputStream

/** Constructor for [[Lz4Compressor]], and `compress` for the default block size. */
object Lz4Compressor {

  /** A [[Compressor]] for LZ4, based on the lz4-java library.
    *
    * @param blockSize
    *   the block size to use. Defaults to 256KiB.
    */
  def apply(
    blockSize: Lz4CompressorBlockSize = Lz4CompressorBlockSize.BlockSize256KiB
  ): Lz4Compressor =
    new Lz4Compressor(blockSize)

  /** See [[apply]] and [[Compressor.compress]]. */
  def compress: ZPipeline[Any, Throwable, Byte, Byte] = apply().compress
}

/** A [[Compressor]] that writes the LZ4 frame format with the lz4-java library (`LZ4FrameOutputStream`). The frame
  * format has no field for a file name. Make one with `Lz4Compressor.apply`, or use `Lz4Compressor.compress` for the
  * default block size of 256 KiB.
  */
final class Lz4Compressor private (blockSize: Lz4CompressorBlockSize) extends Compressor {

  override def compress(implicit trace: Trace): ZPipeline[Any, Throwable, Byte, Byte] = {
    val lz4BlockSize = blockSize match {
      case Lz4CompressorBlockSize.BlockSize64KiB  => BLOCKSIZE.SIZE_64KB
      case Lz4CompressorBlockSize.BlockSize256KiB => BLOCKSIZE.SIZE_256KB
      case Lz4CompressorBlockSize.BlockSize1MiB   => BLOCKSIZE.SIZE_1MB
      case Lz4CompressorBlockSize.BlockSize4MiB   => BLOCKSIZE.SIZE_4MB
    }
    viaOutputStreamByte(new LZ4FrameOutputStream(_, lz4BlockSize))
  }
}

/** Constructor for [[Lz4Decompressor]], and `decompress` for the default chunk size. */
object Lz4Decompressor {

  /** A [[Decompressor]] for LZ4, based on the lz4-java library.
    *
    * @param chunkSize
    *   The maximum chunk size of the outgoing ZStream. Defaults to `ZStream.DefaultChunkSize` (4KiB).
    */
  def apply(chunkSize: Int = ZStream.DefaultChunkSize): Lz4Decompressor =
    new Lz4Decompressor(chunkSize)

  /** See [[apply]] and [[Decompressor.decompress]]. */
  def decompress: ZPipeline[Any, Throwable, Byte, Byte] = apply().decompress
}

/** A [[Decompressor]] that reads the LZ4 frame format with the lz4-java library (`LZ4FrameInputStream`). Input that is
  * not an LZ4 frame, or is empty, fails the stream with a `java.io.IOException`. Make one with `Lz4Decompressor.apply`,
  * or use `Lz4Decompressor.decompress` for the default chunk size.
  */
final class Lz4Decompressor private (chunkSize: Int) extends Decompressor {

  override def decompress(implicit trace: Trace): ZPipeline[Any, Throwable, Byte, Byte] =
    // LZ4FrameInputStream.read does not try to read the requested number of bytes, but it does have a good
    // `available()` implementation, so with buffering we can still get full chunks.
    viaInputStreamByte(chunkSize) { inputStream =>
      new BufferedInputStream(new LZ4FrameInputStream(inputStream), chunkSize)
    }
}
