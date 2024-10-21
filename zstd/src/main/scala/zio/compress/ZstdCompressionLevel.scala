package zio.compress

/** Zstd compression level.
  *
  * @param level
  *   compression level, valid values: -131072 to 22
  */
final case class ZstdCompressionLevel private (level: Int)

object ZstdCompressionLevel {

  /** Makes a Zstd compression level.
    *
    * @param level
    *   compression level, valid values: -131072 to 22
    * @return
    *   a [[ZstdCompressionLevel]] or `None` if the level is not valid
    */
  def apply(level: Int): Option[ZstdCompressionLevel] =
    if (-131072 <= level && level <= 22) Some(new ZstdCompressionLevel(level)) else None

  val CompressionLevelFast7 = new ZstdCompressionLevel(-7)
  val CompressionLevelFast6 = new ZstdCompressionLevel(-6)
  val CompressionLevelFast5 = new ZstdCompressionLevel(-5)
  val CompressionLevelFast4 = new ZstdCompressionLevel(-4)
  val CompressionLevelFast3 = new ZstdCompressionLevel(-3)
  val CompressionLevelFast2 = new ZstdCompressionLevel(-2)
  val CompressionLevelFast1 = new ZstdCompressionLevel(-1)
  val CompressionLevelFast0 = new ZstdCompressionLevel(0)
  val CompressionLevel1 = new ZstdCompressionLevel(1)
  val CompressionLevel2 = new ZstdCompressionLevel(2)
  val CompressionLevel3 = new ZstdCompressionLevel(3)
  val CompressionLevel4 = new ZstdCompressionLevel(4)
  val CompressionLevel5 = new ZstdCompressionLevel(5)
  val CompressionLevel6 = new ZstdCompressionLevel(6)
  val CompressionLevel7 = new ZstdCompressionLevel(7)
  val CompressionLevel8 = new ZstdCompressionLevel(8)
  val CompressionLevel9 = new ZstdCompressionLevel(9)
  val CompressionLevel10 = new ZstdCompressionLevel(10)
  val CompressionLevel11 = new ZstdCompressionLevel(11)
  val CompressionLevel12 = new ZstdCompressionLevel(12)
  val CompressionLevel13 = new ZstdCompressionLevel(13)
  val CompressionLevel14 = new ZstdCompressionLevel(14)
  val CompressionLevel15 = new ZstdCompressionLevel(15)
  val CompressionLevel16 = new ZstdCompressionLevel(16)
  val CompressionLevel17 = new ZstdCompressionLevel(17)
  val CompressionLevel18 = new ZstdCompressionLevel(18)
  val CompressionLevel19 = new ZstdCompressionLevel(19)
  val CompressionLevelUltra20 = new ZstdCompressionLevel(20)
  val CompressionLevelUltra21 = new ZstdCompressionLevel(21)
  val CompressionLevelUltra22 = new ZstdCompressionLevel(22)

  val FastCompressionLevel: ZstdCompressionLevel = CompressionLevelFast4
  val DefaultCompressionLevel: ZstdCompressionLevel = CompressionLevel3
  val HighCompressionLevel: ZstdCompressionLevel = CompressionLevel6
  val VeryHighCompressionLevel: ZstdCompressionLevel = CompressionLevel19
  val UltraCompressionLevel: ZstdCompressionLevel = CompressionLevelUltra22
}
