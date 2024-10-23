package zio.compress

import com.aayushatharva.brotli4j.Brotli4jLoader
import com.aayushatharva.brotli4j.encoder.{BrotliOutputStream, Encoder}
import com.aayushatharva.brotli4j.decoder.BrotliInputStream
import zio._
import zio.compress.BrotliMode._
import zio.compress.JavaIoInterop._
import zio.stream._

object Brotli4JCompressor {

  /** A [[Compressor]] for Brotli, based on the Brotli4J library.
    *
    * @param quality
    *   The compression quality to use, or `None` for the default.
    * @param lgwin
    *   log2(LZ window size) to use, or `None` for the default.
    * @param mode
    *   type of encoding to use, or `None` for the default.
    */
  def apply(
    quality: Option[BrotliQuality] = None,
    lgwin: Option[BrotliLogWindow] = None,
    mode: Option[BrotliMode] = None,
  ): Brotli4JCompressor =
    new Brotli4JCompressor(quality, lgwin, mode)

  /** See [[apply]] and [[Compressor.compress]]. */
  def compress: ZPipeline[Any, Throwable, Byte, Byte] = apply().compress
}

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

  /** See [[apply]] and [[Decompressor.decompress]]. */
  def decompress: ZPipeline[Any, Throwable, Byte, Byte] = apply().decompress
}

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
