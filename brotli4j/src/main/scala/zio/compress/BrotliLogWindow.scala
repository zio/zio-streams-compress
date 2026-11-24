package zio.compress

/** The base-2 logarithm of the Brotli LZ window size, used by [[Brotli4JCompressor]]. Make one with
  * `BrotliLogWindow(lgwin)`, which returns `None` for a value out of range.
  *
  * @param lgwin
  *   log2(LZ window size), valid values: 10 to 24
  */
final case class BrotliLogWindow private (lgwin: Int) extends AnyVal

/** Makes [[BrotliLogWindow]] values. */
object BrotliLogWindow {

  /** Makes a valid Brotli log Window size.
    *
    * @param lgwin
    *   log2(LZ window size), valid values: 10 to 24
    * @return
    *   a [[BrotliLogWindow]] or `None` if the window size is not valid
    */
  def apply(lgwin: Int): Option[BrotliLogWindow] =
    if (10 <= lgwin && lgwin <= 24) Some(new BrotliLogWindow(lgwin)) else None

}
