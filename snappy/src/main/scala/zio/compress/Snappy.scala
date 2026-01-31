package zio.compress

import org.xerial.snappy.{
  SnappyFramedInputStream,
  SnappyFramedOutputStream,
  SnappyHadoopCompatibleOutputStream,
  SnappyInputStream,
  SnappyOutputStream,
}
import zio.Trace
import zio.compress.JavaIoInterop.{viaInputStreamByte, viaOutputStreamByte}
import zio.stream._

import java.io.BufferedInputStream

object SnappyCompressor {

  /** A [[Compressor]] for Snappy, based on https://github.com/xerial/snappy-java library.
    *
    * @param format
    *   the snappy format to write in, defaults to framed format
    *
    * See the [snappy-java compatibility
    * notes](https://github.com/xerial/snappy-java/blob/master/README.md#compatibility-notes) to select the correct
    * format.
    */
  def apply(format: SnappyWriteFormat = SnappyWriteFormat.Framed()): SnappyCompressor =
    new SnappyCompressor(format)

  /** Compresses to framed snappy format. See [[apply]] and [[Compressor.compress]]. */
  def compress: ZPipeline[Any, Throwable, Byte, Byte] = apply().compress
}

final class SnappyCompressor private (format: SnappyWriteFormat) extends Compressor {

  override def compress(implicit trace: Trace): ZPipeline[Any, Throwable, Byte, Byte] =
    viaOutputStreamByte { out =>
      format match {
        case f: SnappyWriteFormat.Framed => new SnappyFramedOutputStream(out, f.blockSize, f.minCompressionRatio)
        case r: SnappyWriteFormat.Basic  => new SnappyOutputStream(out, r.blockSize)
        case h: SnappyWriteFormat.HadoopCompatible => new SnappyHadoopCompatibleOutputStream(out, h.blockSize)
      }
    }
}

object SnappyDecompressor {

  /** A [[Decompressor]] for Snappy, based on https://github.com/xerial/snappy-java library.
    *
    * @param format
    *   the expected snappy format, defaults to framed format
    *
    * See the [snappy-java compatibility
    * notes](https://github.com/xerial/snappy-java/blob/master/README.md#compatibility-notes) to select the correct
    * format.
    * @param chunkSize
    *   The maximum chunk size of the outgoing ZStream. Defaults to `ZStream.DefaultChunkSize` (4KiB).
    */
  def apply(
    format: SnappyReadFormat = SnappyReadFormat.Framed(),
    chunkSize: Int = ZStream.DefaultChunkSize,
  ): SnappyDecompressor =
    new SnappyDecompressor(format, chunkSize)

  /** Decompresses snappy frame format. See [[apply]] and [[Decompressor.decompress]]. */
  def decompress: ZPipeline[Any, Throwable, Byte, Byte] = apply().decompress
}

final class SnappyDecompressor private (format: SnappyReadFormat, chunkSize: Int) extends Decompressor {

  override def decompress(implicit trace: Trace): ZPipeline[Any, Throwable, Byte, Byte] =
    viaInputStreamByte(chunkSize) { inputStream =>
      format match {
        case f: SnappyReadFormat.Framed =>
          // SnappyFrameInputStream.read does not try to read the requested number of bytes, but it does have a good
          // `available()` implementation, so with buffering we can still get full chunks.
          new BufferedInputStream(new SnappyFramedInputStream(inputStream, f.verifyChecksums), chunkSize)
        case r: SnappyReadFormat.Basic =>
          // SnappyInputStream.read does its best to read as many bytes as requested; no buffering needed.
          new SnappyInputStream(inputStream, r.maxChunkSize)
      }
    }
}
