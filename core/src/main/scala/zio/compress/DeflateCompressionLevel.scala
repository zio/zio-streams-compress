package zio.compress

/** Deflate compression level, used for ZIP and GZIP.
  *
  * @param level
  *   compression level, valid values: 0 (no compression) to 9 (maximum compression)
  */
final case class DeflateCompressionLevel private (level: Int)

object DeflateCompressionLevel {

  /** Deflate compression level, used for ZIP and GZIP.
    *
    * @param level
    *   compression level, valid values: 0 (no compression) to 9 (maximum compression)
    * @return
    *   a [[DeflateCompressionLevel]] or `None` if the level is not valid
    */
  def apply(level: Int): Option[DeflateCompressionLevel] =
    if (0 <= level && level <= 9) Some(new DeflateCompressionLevel(level)) else None

  val CompressionLevel0 = new DeflateCompressionLevel(0)
  val CompressionLevel1 = new DeflateCompressionLevel(1)
  val CompressionLevel2 = new DeflateCompressionLevel(2)
  val CompressionLevel3 = new DeflateCompressionLevel(3)
  val CompressionLevel4 = new DeflateCompressionLevel(4)
  val CompressionLevel5 = new DeflateCompressionLevel(5)
  val CompressionLevel6 = new DeflateCompressionLevel(6)
  val CompressionLevel7 = new DeflateCompressionLevel(7)
  val CompressionLevel8 = new DeflateCompressionLevel(8)
  val CompressionLevel9 = new DeflateCompressionLevel(9)

  val NoCompressionLevel: DeflateCompressionLevel = CompressionLevel0
  val FastestCompressionLevel: DeflateCompressionLevel = CompressionLevel1
  val DefaultCompressionLevel: DeflateCompressionLevel = CompressionLevel5
  val BestCompressionLevel: DeflateCompressionLevel = CompressionLevel9
}
