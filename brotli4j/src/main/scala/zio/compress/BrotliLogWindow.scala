package zio.compress

/** Brotli log Window size.
  *
  * @param lgwin
  *   lgwin log2(LZ window size), valid values: 10 to 24
  */
final case class BrotliLogWindow private (lgwin: Int)

object BrotliLogWindow {

  /** Makes a valid Brotli log Window size.
    *
    * @param lgwin
    *   lgwin log2(LZ window size), valid values: 10 to 24
    * @return
    *   a [[BrotliLogWindow]] or `None` if the level is not valid
    */
  def apply(lgwin: Int): Option[BrotliLogWindow] =
    if (10 <= lgwin && lgwin <= 24) Some(new BrotliLogWindow(lgwin)) else None

}
