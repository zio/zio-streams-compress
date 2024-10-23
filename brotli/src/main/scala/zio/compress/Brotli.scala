package zio.compress

import org.brotli.dec.BrotliInputStream
import zio.compress.JavaIoInterop.viaInputStreamByte
import zio.stream._
import zio.Trace

//noinspection ScalaFileName
object BrotliDecompressor {

  /** A [[Decompressor]] for Brotli, based on the official Brotli library.
    *
    * @param customDictionary
    *   a custom dictionary, or `None` for no custom dictionary
    * @param chunkSize
    *   The maximum chunk size of the outgoing ZStream. Defaults to `ZStream.DefaultChunkSize` (4KiB).
    */
  def apply(
    customDictionary: Option[Array[Byte]] = None,
    chunkSize: Int = ZStream.DefaultChunkSize,
  ): BrotliDecompressor =
    new BrotliDecompressor(customDictionary, chunkSize)

  /** See [[apply]] and [[Decompressor.decompress]]. */
  def decompress: ZPipeline[Any, Throwable, Byte, Byte] = apply().decompress
}

//noinspection ScalaFileName
final class BrotliDecompressor private (customDictionary: Option[Array[Byte]], chunkSize: Int) extends Decompressor {

  override def decompress(implicit trace: Trace): ZPipeline[Any, Throwable, Byte, Byte] =
    // BrotliInputStream.read does its best to read as many bytes as requested; no buffering needed.
    viaInputStreamByte(chunkSize) { inputStream =>
      // We don't read byte-by-byte so we set the smallest byte-by-byte buffer size possible.
      val byteByByteReadBufferSize = 1
      new BrotliInputStream(inputStream, byteByByteReadBufferSize, customDictionary.orNull)
    }
}
