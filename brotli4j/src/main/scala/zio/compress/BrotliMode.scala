package zio.compress

sealed trait BrotliMode

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
