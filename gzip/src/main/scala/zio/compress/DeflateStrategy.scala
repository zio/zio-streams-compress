package zio.compress

/** A deflate compression strategy for [[GzipCompressor]]. Without one, the compressor uses the default strategy of
  * `java.util.zip.Deflater`.
  */
sealed trait DeflateStrategy extends Product with Serializable

/** The deflate strategies: `Filtered` and `HuffmanOnly`. */
object DeflateStrategy {

  /** The `java.util.zip.Deflater.FILTERED` strategy, for data made of small values with a somewhat random distribution,
    * such as the output of a filter or predictor.
    */
  case object Filtered extends DeflateStrategy

  /** The `java.util.zip.Deflater.HUFFMAN_ONLY` strategy: only Huffman coding, with no string matching. It usually gives
    * larger output than the default strategy.
    */
  case object HuffmanOnly extends DeflateStrategy
}
