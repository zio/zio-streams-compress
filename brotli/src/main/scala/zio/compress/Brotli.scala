package zio.compress

import zio.stream._
import org.brotli.dec.BrotliInputStream
import zio.compress.JavaIoInterop.viaInputStreamByte

//noinspection ScalaFileName
object BrotliDecompressor {

  /**
   * Makes a pipeline that accepts a Brotli compressed byte stream and produces a decompressed byte stream.
   *
   * @param customDictionary
   *   a custom dictionary, or `None` for no custom dictionary
   * @param chunkSize
   *   The maximum chunk size of the outgoing ZStream. Defaults to `ZStream.DefaultChunkSize` (4KiB).
   */
  def make(
    customDictionary: Option[Array[Byte]] = None,
    chunkSize: Int = ZStream.DefaultChunkSize,
  ): BrotliDecompressor =
    new BrotliDecompressor(customDictionary, chunkSize)
}

//noinspection ScalaFileName
class BrotliDecompressor private (customDictionary: Option[Array[Byte]], chunkSize: Int) extends Decompressor {
  override def decompress: ZPipeline[Any, Throwable, Byte, Byte] =
    // BrotliInputStream.read does its best to read as many bytes as requested; no buffering needed.
    viaInputStreamByte(chunkSize) { inputStream =>
      // We don't read byte-by-byte so we set the smallest byte-by-byte buffer size possible.
      val byteByByteReadBufferSize = 1
      new BrotliInputStream(inputStream, byteByByteReadBufferSize, customDictionary.orNull)
    }
}
