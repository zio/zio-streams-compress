package zio.compress

import zio.Trace
import zio.stream._

/** Decompresses a byte stream. The pipeline from `decompress` takes compressed bytes and emits the original bytes.
  *
  * The gzip, zstd, bzip2, brotli, brotli4j, lz4 and snappy modules each provide a `Decompressor`, and an
  * [[ArchiveSingleFileDecompressor]] turns an [[Unarchiver]] into one. Write code against `Decompressor` to accept any
  * of them, and pass `Decompressor.empty` for input that is not compressed.
  *
  * {{{
  * import zio.compress._
  * import zio.stream._
  *
  * def readText(
  *   decompressor: Decompressor,
  *   compressed: ZStream[Any, Throwable, Byte],
  * ): ZStream[Any, Throwable, String] =
  *   compressed.via(decompressor.decompress).via(ZPipeline.utf8Decode)
  *
  * val text: ZStream[Any, Throwable, String] =
  *   readText(Decompressor.empty, ZStream.fromIterable("Call me Ishmael.".getBytes("UTF-8")))
  * }}}
  *
  * @see
  *   [[https://zio.dev/zio-streams-compress/reference-core/ ZIO Streams Compress: core reference]]
  */
trait Decompressor extends Serializable {

  /** A pipeline that decompresses a byte stream to an uncompressed byte stream. */
  def decompress(implicit trace: Trace): ZPipeline[Any, Throwable, Byte, Byte]
}

/** Holds `Decompressor.empty`, the decompressor that leaves bytes unchanged. */
object Decompressor {

  /** A decompressor that does nothing; it passes all bytes through unchanged. */
  def empty: Decompressor = new Decompressor {

    override def decompress(implicit trace: Trace): ZPipeline[Any, Nothing, Byte, Byte] =
      ZPipeline.identity
  }
}
