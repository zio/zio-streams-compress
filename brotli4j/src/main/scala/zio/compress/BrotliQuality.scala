package zio.compress

/** A Brotli compression quality, used by [[Brotli4JCompressor]]. Make one with `BrotliQuality(level)`, which returns
  * `None` for a level out of range, or use one of the named constants. Higher qualities usually give smaller output.
  *
  * @param level
  *   compression level, valid values: 0 to 11
  */
final case class BrotliQuality private (level: Int) extends AnyVal

/** Makes [[BrotliQuality]] values and names each quality from 0 to 11. */
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

  /** Quality 0, the lowest quality. */
  final val Quality0 = new BrotliQuality(0)

  /** Quality 1. */
  final val Quality1 = new BrotliQuality(1)

  /** Quality 2. */
  final val Quality2 = new BrotliQuality(2)

  /** Quality 3. */
  final val Quality3 = new BrotliQuality(3)

  /** Quality 4. */
  final val Quality4 = new BrotliQuality(4)

  /** Quality 5. */
  final val Quality5 = new BrotliQuality(5)

  /** Quality 6. */
  final val Quality6 = new BrotliQuality(6)

  /** Quality 7. */
  final val Quality7 = new BrotliQuality(7)

  /** Quality 8. */
  final val Quality8 = new BrotliQuality(8)

  /** Quality 9. */
  final val Quality9 = new BrotliQuality(9)

  /** Quality 10. */
  final val Quality10 = new BrotliQuality(10)

  /** Quality 11, the highest quality and the library default. */
  final val Quality11 = new BrotliQuality(11)
}
