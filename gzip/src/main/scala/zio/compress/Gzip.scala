package zio.compress

import zio.Trace
import zio.compress.DeflateStrategy.{Filtered, HuffmanOnly}
import zio.stream._

object GzipCompressor {

  /** A [[Compressor]] for Gzip, based on ZIO's native support (which uses the JVM standard library).
    *
    * @param deflateLevel
    *   the deflate compression level
    * @param deflateStrategy
    *   a deflate compression strategy
    * @param bufferSize
    *   the maximum chunk size of the outgoing ZStream. Defaults to 64KiB.
    */
  def apply(
    deflateLevel: Option[DeflateCompressionLevel] = None,
    deflateStrategy: Option[DeflateStrategy] = None,
    bufferSize: Int = Defaults.DefaultChunkSize,
  ): GzipCompressor =
    new GzipCompressor(deflateLevel, deflateStrategy, bufferSize)

  /** See [[apply]] and [[Compressor.compress]]. */
  def compress: ZPipeline[Any, Nothing, Byte, Byte] = apply().compress
}

final class GzipCompressor private (
  deflateLevel: Option[DeflateCompressionLevel],
  deflateStrategy: Option[DeflateStrategy],
  bufferSize: Int,
) extends Compressor {

  /** @inheritdoc */
  override def compress(implicit trace: Trace): ZPipeline[Any, Nothing, Byte, Byte] =
    ZPipeline.gzip(
      bufferSize,
      Parameters.levelToZio(deflateLevel),
      Parameters.strategyToZio(deflateStrategy),
    )
}

object GzipDecompressor {

  /** A [[Decompressor]] for Gzip, based on ZIO's native support (which uses the JVM standard library).
    *
    * @param bufferSize
    *   the used buffer size. Defaults to 64KiB.
    */
  def apply(bufferSize: Int = Defaults.DefaultChunkSize): GzipDecompressor =
    new GzipDecompressor(bufferSize)

  /** See [[apply]] and [[Decompressor.decompress]]. */
  def decompress: ZPipeline[Any, Throwable, Byte, Byte] = apply().decompress
}

final class GzipDecompressor private (bufferSize: Int) extends Decompressor {

  /** @inheritdoc */
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
