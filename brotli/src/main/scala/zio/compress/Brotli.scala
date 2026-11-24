package zio.compress

import org.brotli.dec.BrotliInputStream
import zio.compress.JavaIoInterop.viaInputStreamByte
import zio.stream._
import zio.Trace

//noinspection ScalaFileName
/** Creates [[BrotliDecompressor]]s: `apply` takes the options, and `decompress` gives the pipeline with the default
  * options.
  */
object BrotliDecompressor {

  /** A [[Decompressor]] for Brotli, based on the official Brotli Java decoder (`org.brotli:dec`).
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

  /** Returns the decompression pipeline of a [[BrotliDecompressor]] with the default options, the same as
    * `BrotliDecompressor().decompress`.
    */
  def decompress: ZPipeline[Any, Throwable, Byte, Byte] = apply().decompress
}

//noinspection ScalaFileName
/** A [[Decompressor]] that reads the Brotli format, using the official Brotli decoder for Java (`org.brotli:dec`),
  * which is written in Java and loads no native library. It only decompresses: to compress Brotli, use
  * `Brotli4JCompressor` from the `zio-streams-compress-brotli4j` module. Input that is not Brotli, input that is cut
  * short, and empty input fail the stream with `java.io.IOException: Brotli stream decoding failed`.
  */
final class BrotliDecompressor private (customDictionary: Option[Array[Byte]], chunkSize: Int) extends Decompressor {

  override def decompress(implicit trace: Trace): ZPipeline[Any, Throwable, Byte, Byte] =
    // BrotliInputStream.read does its best to read as many bytes as requested; no buffering needed.
    viaInputStreamByte(chunkSize) { inputStream =>
      // We don't read byte-by-byte so we set the smallest byte-by-byte buffer size possible.
      val byteByByteReadBufferSize = 1
      new BrotliInputStream(inputStream, byteByByteReadBufferSize, customDictionary.orNull)
    }
}
