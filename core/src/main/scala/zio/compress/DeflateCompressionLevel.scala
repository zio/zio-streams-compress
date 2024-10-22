package zio.compress

/** Deflate compression level, used for ZIP and GZIP.
  *
  * @param level
  *   compression level, valid values: 0 (no compression) to 9 (maximum compression)
  */
final case class DeflateCompressionLevel private (level: Int) extends AnyVal

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

  final val CompressionLevel0 = new DeflateCompressionLevel(0)
  final val CompressionLevel1 = new DeflateCompressionLevel(1)
  final val CompressionLevel2 = new DeflateCompressionLevel(2)
  final val CompressionLevel3 = new DeflateCompressionLevel(3)
  final val CompressionLevel4 = new DeflateCompressionLevel(4)
  final val CompressionLevel5 = new DeflateCompressionLevel(5)
  final val CompressionLevel6 = new DeflateCompressionLevel(6)
  final val CompressionLevel7 = new DeflateCompressionLevel(7)
  final val CompressionLevel8 = new DeflateCompressionLevel(8)
  final val CompressionLevel9 = new DeflateCompressionLevel(9)

  final val NoCompressionLevel: DeflateCompressionLevel = CompressionLevel0
  final val FastestCompressionLevel: DeflateCompressionLevel = CompressionLevel1
  final val DefaultCompressionLevel: DeflateCompressionLevel = CompressionLevel5
  final val BestCompressionLevel: DeflateCompressionLevel = CompressionLevel9
}
