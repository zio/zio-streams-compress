package zio.compress

import zio.stream._
import zio.stream.compression.CompressionLevel._
import zio.stream.compression.CompressionStrategy._
import zio.stream.compression.{CompressionLevel, CompressionStrategy}

object GzipCompressor {
  private val CompressionLevels = Seq(
    DefaultCompression,
    NoCompression,
    BestSpeed,
    CompressionLevel2,
    CompressionLevel3,
    CompressionLevel4,
    CompressionLevel5,
    CompressionLevel6,
    CompressionLevel7,
    CompressionLevel8,
    BestCompression,
  )
  private val CompressionStrategies = Seq(DefaultStrategy, Filtered, HuffmanOnly)

  /** Converts a deflate compression level from `Int` to [[zio.stream.compression.CompressionLevel]].
    *
    * @param level
    *   a deflate compression level, valid values: -1 (default), 0 (no compression), 1 (fastest) to 9 (best compression)
    */
  def intToCompressionLevel(level: Int): Option[CompressionLevel] =
    CompressionLevels.find(_.jValue == level)

  /** Converts a deflate compression strategy from `Int` to [[zio.stream.compression.CompressionStrategy]].
    *
    * @param strategy
    *   a deflate compression strategy, valid values: 0 (default), 1 (filtered) or 2 (huffman only)
    */
  def intToCompressionStrategy(strategy: Int): Option[CompressionStrategy] =
    CompressionStrategies.find(_.jValue == strategy)

  /** Make a pipeline that accepts a stream of bytes and produces a stream with Gzip compressed bytes.
    *
    * @param deflateLevel
    *   the deflate compression level
    * @param deflateStrategy
    *   a deflate compression strategy, valid values: 0 (default), 1 (filtered) or 2 (huffman only)
    * @param bufferSize
    *   the maximum chunk size of the outgoing ZStream. Defaults to 64KiB.
    */
  def make(
    deflateLevel: Option[CompressionLevel] = None,
    deflateStrategy: Option[CompressionStrategy] = None,
    bufferSize: Int = Defaults.DefaultChunkSize,
  ): GzipCompressor =
    new GzipCompressor(
      deflateLevel.getOrElse(DefaultCompression),
      deflateStrategy.getOrElse(CompressionStrategy.DefaultStrategy),
      bufferSize,
    )
}

class GzipCompressor private (
  deflateLevel: CompressionLevel,
  deflateStrategy: CompressionStrategy,
  bufferSize: Int,
) extends Compressor {
  override def compress: ZPipeline[Any, Nothing, Byte, Byte] =
    ZPipeline.gzip(bufferSize, deflateLevel, deflateStrategy)
}

object GzipDecompressor {

  /** Makes a pipeline that accepts a Gzip compressed byte stream and produces a decompressed byte stream.
    *
    * @param bufferSize
    *   the used buffer size. Defaults to 64KiB.
    */
  def make(bufferSize: Int = Defaults.DefaultChunkSize): GzipDecompressor =
    new GzipDecompressor(bufferSize)
}

class GzipDecompressor private (bufferSize: Int) extends Decompressor {
  override def decompress: ZPipeline[Any, Throwable, Byte, Byte] =
    ZPipeline.gunzip(bufferSize)
}
