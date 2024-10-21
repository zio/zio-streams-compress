package zio.compress

/** Brotli compression level.
  *
  * @param level
  *   compression level, valid values: 0 to 11
  */
final case class BrotliQuality private (level: Int)

object BrotliQuality {

  /** Makes a Brotli compression level.
    *
    * @param level
    *   compression level, valid values: 0 to 11
    * @return
    *   a [[BrotliQuality]] or `None` if the level is not valid
    */
  def apply(level: Int): Option[BrotliQuality] =
    if (0 <= level && level <= 11) Some(new BrotliQuality(level)) else None

  val Quality0 = new BrotliQuality(0)
  val Quality1 = new BrotliQuality(1)
  val Quality2 = new BrotliQuality(2)
  val Quality3 = new BrotliQuality(3)
  val Quality4 = new BrotliQuality(4)
  val Quality5 = new BrotliQuality(5)
  val Quality6 = new BrotliQuality(6)
  val Quality7 = new BrotliQuality(7)
  val Quality8 = new BrotliQuality(8)
  val Quality9 = new BrotliQuality(9)
  val Quality10 = new BrotliQuality(10)
  val Quality11 = new BrotliQuality(11)
}
