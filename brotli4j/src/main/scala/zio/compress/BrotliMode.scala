package zio.compress

/** A hint to the Brotli encoder of [[Brotli4JCompressor]] about the kind of input: generic data, UTF-8 text, or a font.
  */
sealed trait BrotliMode extends Product with Serializable

/** The Brotli modes: `Generic`, `Text` and `Font`. */
object BrotliMode {

  /** Default compression mode. In this mode compressor does not know anything in advance about the properties of the
    * input.
    */
  case object Generic extends BrotliMode

  /** Compression mode for UTF-8 formatted text input.
    */
  case object Text extends BrotliMode

  /** Compression mode used in WOFF 2.0.
    */
  case object Font extends BrotliMode
}
