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

  val CompressionLevel0 = new BrotliQuality(0)
  val CompressionLevel1 = new BrotliQuality(1)
  val CompressionLevel2 = new BrotliQuality(2)
  val CompressionLevel3 = new BrotliQuality(3)
  val CompressionLevel4 = new BrotliQuality(4)
  val CompressionLevel5 = new BrotliQuality(5)
  val CompressionLevel6 = new BrotliQuality(6)
  val CompressionLevel7 = new BrotliQuality(7)
  val CompressionLevel8 = new BrotliQuality(8)
  val CompressionLevel9 = new BrotliQuality(9)
  val CompressionLevel10 = new BrotliQuality(10)
  val CompressionLevel11 = new BrotliQuality(11)
}
