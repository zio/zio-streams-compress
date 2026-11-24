package zio.compress

import com.aayushatharva.brotli4j.Brotli4jLoader
import com.aayushatharva.brotli4j.encoder.{BrotliOutputStream, Encoder}
import com.aayushatharva.brotli4j.decoder.BrotliInputStream
import zio._
import zio.compress.BrotliMode._
import zio.compress.JavaIoInterop._
import zio.stream._

/** Creates [[Brotli4JCompressor]]s: `apply` takes the options, and `compress` gives the pipeline with the default
  * options.
  */
object Brotli4JCompressor {

  /** A [[Compressor]] for Brotli, based on the Brotli4J library.
    *
    * @param quality
    *   The compression quality to use, or `None` for the library default, which gives the same output as quality 11.
    * @param lgwin
    *   log2(LZ window size) to use, or `None` for the library default, which gives the same output as 22.
    * @param mode
    *   type of encoding to use, or `None` for the library default, which gives the same output as `BrotliMode.Generic`.
    */
  def apply(
    quality: Option[BrotliQuality] = None,
    lgwin: Option[BrotliLogWindow] = None,
    mode: Option[BrotliMode] = None,
  ): Brotli4JCompressor =
    new Brotli4JCompressor(quality, lgwin, mode)

  /** Returns the compression pipeline of a [[Brotli4JCompressor]] with the default options, the same as
    * `Brotli4JCompressor().compress`.
    */
  def compress: ZPipeline[Any, Throwable, Byte, Byte] = apply().compress
}

/** A [[Compressor]] that writes the Brotli format, through the native Brotli library that Brotli4J
  * (`com.aayushatharva.brotli4j:brotli4j`) loads. Use it to produce Brotli data, for example `.br` files or HTTP
  * content with `Content-Encoding: br`. Create one with `Brotli4JCompressor(...)` to choose the quality, the window
  * size and the mode, or use `Brotli4JCompressor.compress` for the library defaults (quality 11, window 22, generic
  * mode). Read the result back with [[Brotli4JDecompressor]].
  *
  * The native library must load on the platform that runs the pipeline. When it cannot be loaded, the stream neither
  * fails nor ends.
  *
  * {{{
  * import zio._
  * import zio.compress._
  * import zio.stream._
  *
  * val page: String = "<html><body>" + ("<p>Scala books on sale</p>" * 40) + "</body></html>"
  *
  * // compressedSize succeeds with 58 (bytes, from 1066 input bytes)
  * val compressedSize: Task[Long] =
  *   ZStream
  *     .fromIterable(page.getBytes("UTF-8"))
  *     .via(Brotli4JCompressor(quality = Some(BrotliQuality.Quality5), lgwin = BrotliLogWindow(18), mode = Some(BrotliMode.Text)).compress)
  *     .runCount
  * }}}
  *
  * @see
  *   [[https://zio.dev/zio-streams-compress/reference-brotli4j/ Brotli4J]]
  */
final class Brotli4JCompressor private (
  quality: Option[BrotliQuality],
  lgwin: Option[BrotliLogWindow],
  mode: Option[BrotliMode],
) extends Compressor {

  override def compress(implicit trace: Trace): ZPipeline[Any, Throwable, Byte, Byte] =
    BrotliLoader.ensureAvailability() >>>
      viaOutputStreamByte { outputStream =>
        val brotliMode = mode.map {
          case Generic => Encoder.Mode.GENERIC
          case Text    => Encoder.Mode.TEXT
          case Font    => Encoder.Mode.FONT
        }
        val params = new Encoder.Parameters()
          .setQuality(quality.map(_.level).getOrElse(-1))
          .setWindow(lgwin.map(_.lgwin).getOrElse(-1))
          .setMode(brotliMode.orNull)
        new BrotliOutputStream(outputStream, params)
      }
}

/** Creates [[Brotli4JDecompressor]]s: `apply` takes the options, and `decompress` gives the pipeline with the default
  * options.
  */
object Brotli4JDecompressor {

  /** A [[Decompressor]] for Brotli, based on the Brotli4J library.
    *
    * @param chunkSize
    *   The maximum chunk size of the outgoing ZStream. Defaults to `ZStream.DefaultChunkSize` (4KiB).
    */
  def apply(
    chunkSize: Int = ZStream.DefaultChunkSize
  ): Brotli4JDecompressor =
    new Brotli4JDecompressor(chunkSize)

  /** Returns the decompression pipeline of a [[Brotli4JDecompressor]] with the default chunk size, the same as
    * `Brotli4JDecompressor().decompress`.
    */
  def decompress: ZPipeline[Any, Throwable, Byte, Byte] = apply().decompress
}

/** A [[Decompressor]] that reads the Brotli format, through the native Brotli library that Brotli4J
  * (`com.aayushatharva.brotli4j:brotli4j`) loads. Use it to read Brotli data, such as `.br` files or the output of
  * [[Brotli4JCompressor]]. Create one with `Brotli4JDecompressor(chunkSize)` to set the largest chunk it emits, or use
  * `Brotli4JDecompressor.decompress` for the default of 4 KiB. It has no dictionary option.
  *
  * Input that is not Brotli fails the stream with `java.io.IOException: corrupted input`, and input that is cut short
  * or empty fails with `java.io.IOException: unexpected end of input`. When the native library cannot be loaded, the
  * stream dies with a `java.lang.UnsatisfiedLinkError`.
  *
  * {{{
  * import zio._
  * import zio.compress._
  * import zio.stream._
  *
  * val isbn: String = "ISBN 978-0-9815316-1-8"
  *
  * // roundTrip succeeds with "ISBN 978-0-9815316-1-8"
  * val roundTrip: Task[String] =
  *   ZStream
  *     .fromIterable(isbn.getBytes("UTF-8"))
  *     .via(Brotli4JCompressor.compress)
  *     .via(Brotli4JDecompressor(chunkSize = 8192).decompress)
  *     .runCollect
  *     .map(bytes => new String(bytes.toArray, "UTF-8"))
  * }}}
  *
  * @see
  *   [[https://zio.dev/zio-streams-compress/reference-brotli4j/ Brotli4J]]
  */
final class Brotli4JDecompressor private (chunkSize: Int) extends Decompressor {

  override def decompress(implicit trace: Trace): ZPipeline[Any, Throwable, Byte, Byte] =
    BrotliLoader.ensureAvailability() >>>
      viaInputStreamByte(chunkSize) { inputStream =>
        new BrotliInputStream(inputStream)
      }
}

private object BrotliLoader {
  // Trigger loading of the Brotli4j native library
  new Brotli4jLoader()

  def ensureAvailability(): ZPipeline[Any, Throwable, Byte, Byte] =
    ZPipeline.unwrap {
      ZIO
        .attemptBlocking(Brotli4jLoader.ensureAvailability())
        .as(ZPipeline.identity[Byte])
    }
}
