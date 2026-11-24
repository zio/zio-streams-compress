package zio.compress

import zio.stream._
import zio.Trace

/** Compresses a byte stream. The pipeline from `compress` takes raw bytes and emits compressed bytes.
  *
  * The gzip, zstd, bzip2, brotli4j, lz4 and snappy modules each provide a `Compressor`, and an
  * [[ArchiveSingleFileCompressor]] turns an [[Archiver]] into one. Write code against `Compressor` to accept any of
  * them, and pass `Compressor.empty` when the caller wants no compression.
  *
  * {{{
  * import zio.compress._
  * import zio.stream._
  *
  * def compressText(compressor: Compressor, text: String): ZStream[Any, Throwable, Byte] =
  *   ZStream.fromIterable(text.getBytes("UTF-8")).via(compressor.compress)
  *
  * val uncompressed: ZStream[Any, Throwable, Byte] = compressText(Compressor.empty, "Moby-Dick; or, The Whale")
  * }}}
  *
  * @see
  *   [[https://zio.dev/zio-streams-compress/reference-core/ ZIO Streams Compress: core reference]]
  */
trait Compressor extends Serializable {

  /** A pipeline that takes a raw byte stream and produces a compressed byte stream. */
  def compress(implicit trace: Trace): ZPipeline[Any, Throwable, Byte, Byte]
}

/** Holds `Compressor.empty`, the compressor that leaves bytes unchanged. */
object Compressor extends Serializable {

  /** A compressor that does nothing; it passes all bytes through unchanged. */
  def empty: Compressor = new Compressor {

    override def compress(implicit trace: Trace): ZPipeline[Any, Nothing, Byte, Byte] =
      ZPipeline.identity
  }
}
