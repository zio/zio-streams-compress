package zio.compress

import zio.Trace
import zio.compress.DeflateStrategy.{Filtered, HuffmanOnly}
import zio.stream._

/** Creates [[GzipCompressor]]s: `apply` takes the options, and `compress` gives the pipeline with the default options.
  */
object GzipCompressor {

  /** A [[Compressor]] for Gzip, based on ZIO's native support (which uses the JVM standard library).
    *
    * @param deflateLevel
    *   the deflate compression level, or `None` for the JVM default level, which gives the same output as level 6.
    *   Defaults to `None`.
    * @param deflateStrategy
    *   a deflate compression strategy, or `None` for the default strategy of `java.util.zip.Deflater`. Defaults to
    *   `None`.
    * @param bufferSize
    *   the size of the buffer that the deflater writes its output into. Outgoing chunks can be larger than this size.
    *   Defaults to 64KiB.
    */
  def apply(
    deflateLevel: Option[DeflateCompressionLevel] = None,
    deflateStrategy: Option[DeflateStrategy] = None,
    bufferSize: Int = Defaults.DefaultChunkSize,
  ): GzipCompressor =
    new GzipCompressor(deflateLevel, deflateStrategy, bufferSize)

  /** Returns the compression pipeline of a [[GzipCompressor]] with the default options, the same as
    * `GzipCompressor().compress`. Its error type is `Nothing`.
    */
  def compress: ZPipeline[Any, Nothing, Byte, Byte] = apply().compress
}

/** A [[Compressor]] that writes the Gzip format, using `ZPipeline.gzip` from ZIO Streams, which runs
  * `java.util.zip.Deflater`. Use it to produce Gzip data, such as the content of a `.gz` file. Create one with
  * `GzipCompressor(...)` to choose the deflate level, the strategy and the buffer size, or use
  * `GzipCompressor.compress` for the defaults. Read the result back with [[GzipDecompressor]].
  *
  * Its pipeline has the error type `Nothing`: it fails only when its upstream fails.
  *
  * {{{
  * import zio._
  * import zio.compress._
  * import zio.stream._
  *
  * val review: Chunk[Byte] = Chunk.fromArray(("Scala for the Impatient: 5 stars\n" * 100).getBytes("UTF-8"))
  *
  * // gzipped succeeds with 82 bytes (from 3300 input bytes) that start with 31, -117 (0x1f, 0x8b), the Gzip magic bytes
  * val gzipped: UIO[Chunk[Byte]] =
  *   ZStream
  *     .fromChunk(review)
  *     .via(GzipCompressor(deflateLevel = DeflateCompressionLevel(9), deflateStrategy = Some(DeflateStrategy.Filtered)).compress)
  *     .runCollect
  * }}}
  *
  * @see
  *   [[https://zio.dev/zio-streams-compress/reference-gzip/ Gzip]]
  */
final class GzipCompressor private (
  deflateLevel: Option[DeflateCompressionLevel],
  deflateStrategy: Option[DeflateStrategy],
  bufferSize: Int,
) extends Compressor {

  override def compress(implicit trace: Trace): ZPipeline[Any, Nothing, Byte, Byte] =
    ZPipeline.gzip(
      bufferSize,
      Parameters.levelToZio(deflateLevel),
      Parameters.strategyToZio(deflateStrategy),
    )
}

/** Creates [[GzipDecompressor]]s: `apply` takes the options, and `decompress` gives the pipeline with the default
  * options.
  */
object GzipDecompressor {

  /** A [[Decompressor]] for Gzip, based on ZIO's native support (which uses the JVM standard library).
    *
    * @param bufferSize
    *   the size of the buffer that the inflater writes its output into. Outgoing chunks can be larger than this size.
    *   Defaults to 64KiB.
    */
  def apply(bufferSize: Int = Defaults.DefaultChunkSize): GzipDecompressor =
    new GzipDecompressor(bufferSize)

  /** Returns the decompression pipeline of a [[GzipDecompressor]] with the default buffer size, the same as
    * `GzipDecompressor().decompress`.
    */
  def decompress: ZPipeline[Any, Throwable, Byte, Byte] = apply().decompress
}

/** A [[Decompressor]] that reads the Gzip format, using `ZPipeline.gunzip` from ZIO Streams, which runs
  * `java.util.zip.Inflater`. Use it to read Gzip data, such as the content of a `.gz` file or the output of
  * [[GzipCompressor]]. Create one with `GzipDecompressor(bufferSize)` to set the buffer size, or use
  * `GzipDecompressor.decompress` for the default of 64 KiB. Input made of several Gzip members one after the other
  * gives their contents in order.
  *
  * Input that does not start with a Gzip header fails the stream with
  * `zio.stream.compression.CompressionException: Invalid GZIP header`, and input that is cut short fails with
  * `CompressionException: Stream closed before completion.` Empty input gives empty output.
  *
  * {{{
  * import zio._
  * import zio.compress._
  * import zio.stream._
  *
  * // restored succeeds with "Effective Scala"
  * val restored: Task[String] =
  *   ZStream
  *     .fromIterable("Effective Scala".getBytes("UTF-8"))
  *     .via(GzipCompressor.compress)
  *     .via(GzipDecompressor(bufferSize = 8192).decompress)
  *     .runCollect
  *     .map(bytes => new String(bytes.toArray, "UTF-8"))
  *
  * // notGzip fails with CompressionException: Invalid GZIP header
  * val notGzip: Task[Chunk[Byte]] =
  *   ZStream
  *     .fromIterable("Effective Scala".getBytes("UTF-8"))
  *     .via(GzipDecompressor.decompress)
  *     .runCollect
  * }}}
  *
  * @see
  *   [[https://zio.dev/zio-streams-compress/reference-gzip/ Gzip]]
  */
final class GzipDecompressor private (bufferSize: Int) extends Decompressor {

  override def decompress(implicit trace: Trace): ZPipeline[Any, Throwable, Byte, Byte] =
    ZPipeline.gunzip(bufferSize)
}

private object Parameters {
  private val ZioCompressionLevels = IndexedSeq(
    zio.stream.compression.CompressionLevel.NoCompression,
    zio.stream.compression.CompressionLevel.BestSpeed,
    zio.stream.compression.CompressionLevel.CompressionLevel2,
    zio.stream.compression.CompressionLevel.CompressionLevel3,
    zio.stream.compression.CompressionLevel.CompressionLevel4,
    zio.stream.compression.CompressionLevel.CompressionLevel5,
    zio.stream.compression.CompressionLevel.CompressionLevel6,
    zio.stream.compression.CompressionLevel.CompressionLevel7,
    zio.stream.compression.CompressionLevel.CompressionLevel8,
    zio.stream.compression.CompressionLevel.BestCompression,
  )

  def levelToZio(level: Option[DeflateCompressionLevel]): zio.stream.compression.CompressionLevel =
    level match {
      case Some(l) => ZioCompressionLevels
          .find(_.jValue == l.level)
          .getOrElse(sys.error(s"BUG: Invalid compression level: ${l.level}"))
      case None =>
        zio.stream.compression.CompressionLevel.DefaultCompression
    }

  def strategyToZio(strategy: Option[DeflateStrategy]): zio.stream.compression.CompressionStrategy =
    strategy match {
      case Some(Filtered)    => zio.stream.compression.CompressionStrategy.Filtered
      case Some(HuffmanOnly) => zio.stream.compression.CompressionStrategy.HuffmanOnly
      case None              => zio.stream.compression.CompressionStrategy.DefaultStrategy
    }
}
