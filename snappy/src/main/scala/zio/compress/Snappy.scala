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

/** Constructor for [[SnappyCompressor]], and `compress` for the framed format. */
object SnappyCompressor {

  /** A [[Compressor]] for Snappy, based on the [[https://github.com/xerial/snappy-java snappy-java]] library.
    *
    * See the
    * [[https://github.com/xerial/snappy-java/blob/main/README.md#compatibility-notes snappy-java compatibility notes]]
    * to select the correct format.
    *
    * @param format
    *   the snappy format to write in, defaults to framed format
    */
  def apply(format: SnappyWriteFormat = SnappyWriteFormat.Framed()): SnappyCompressor =
    new SnappyCompressor(format)

  /** Compresses to framed snappy format. See [[apply]] and [[Compressor.compress]]. */
  def compress: ZPipeline[Any, Throwable, Byte, Byte] = apply().compress
}

/** A [[Compressor]] that writes Snappy with the snappy-java library, in the [[SnappyWriteFormat]] it was made with:
  * framed (the default), basic, or Hadoop-compatible. Make one with `SnappyCompressor.apply`, or use
  * `SnappyCompressor.compress` for the framed format.
  */
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

/** Constructor for [[SnappyDecompressor]], and `decompress` for the framed format. */
object SnappyDecompressor {

  /** A [[Decompressor]] for Snappy, based on the [[https://github.com/xerial/snappy-java snappy-java]] library.
    *
    * See the
    * [[https://github.com/xerial/snappy-java/blob/main/README.md#compatibility-notes snappy-java compatibility notes]]
    * to select the correct format.
    *
    * @param format
    *   the expected snappy format, defaults to framed format
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

/** A [[Decompressor]] that reads Snappy with the snappy-java library, in the [[SnappyReadFormat]] it was made with:
  * framed (the default) or basic. Read data with the format it was written with; data in the other format makes the
  * stream die with a `java.io.IOException`, for example `invalid stream header` for basic data read as framed. There is
  * no read format for the Hadoop-compatible write format. Make one with `SnappyDecompressor.apply`, or use
  * `SnappyDecompressor.decompress` for the framed format.
  */
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
