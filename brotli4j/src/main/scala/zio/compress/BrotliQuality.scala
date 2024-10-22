package zio.compress

/** Brotli compression level.
  *
  * @param level
  *   compression level, valid values: 0 to 11
  */
final case class BrotliQuality private (level: Int) extends AnyVal

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

  final val Quality0 = new BrotliQuality(0)
  final val Quality1 = new BrotliQuality(1)
  final val Quality2 = new BrotliQuality(2)
  final val Quality3 = new BrotliQuality(3)
  final val Quality4 = new BrotliQuality(4)
  final val Quality5 = new BrotliQuality(5)
  final val Quality6 = new BrotliQuality(6)
  final val Quality7 = new BrotliQuality(7)
  final val Quality8 = new BrotliQuality(8)
  final val Quality9 = new BrotliQuality(9)
  final val Quality10 = new BrotliQuality(10)
  final val Quality11 = new BrotliQuality(11)
}
