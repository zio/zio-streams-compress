package zio.compress

/** Zstd compression level.
  *
  * @param level
  *   compression level, valid values: -131072 to 22
  */
final case class ZstdCompressionLevel private (level: Int) extends AnyVal

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

  final val CompressionLevelFast7 = new ZstdCompressionLevel(-7)
  final val CompressionLevelFast6 = new ZstdCompressionLevel(-6)
  final val CompressionLevelFast5 = new ZstdCompressionLevel(-5)
  final val CompressionLevelFast4 = new ZstdCompressionLevel(-4)
  final val CompressionLevelFast3 = new ZstdCompressionLevel(-3)
  final val CompressionLevelFast2 = new ZstdCompressionLevel(-2)
  final val CompressionLevelFast1 = new ZstdCompressionLevel(-1)
  final val CompressionLevelFast0 = new ZstdCompressionLevel(0)
  final val CompressionLevel1 = new ZstdCompressionLevel(1)
  final val CompressionLevel2 = new ZstdCompressionLevel(2)
  final val CompressionLevel3 = new ZstdCompressionLevel(3)
  final val CompressionLevel4 = new ZstdCompressionLevel(4)
  final val CompressionLevel5 = new ZstdCompressionLevel(5)
  final val CompressionLevel6 = new ZstdCompressionLevel(6)
  final val CompressionLevel7 = new ZstdCompressionLevel(7)
  final val CompressionLevel8 = new ZstdCompressionLevel(8)
  final val CompressionLevel9 = new ZstdCompressionLevel(9)
  final val CompressionLevel10 = new ZstdCompressionLevel(10)
  final val CompressionLevel11 = new ZstdCompressionLevel(11)
  final val CompressionLevel12 = new ZstdCompressionLevel(12)
  final val CompressionLevel13 = new ZstdCompressionLevel(13)
  final val CompressionLevel14 = new ZstdCompressionLevel(14)
  final val CompressionLevel15 = new ZstdCompressionLevel(15)
  final val CompressionLevel16 = new ZstdCompressionLevel(16)
  final val CompressionLevel17 = new ZstdCompressionLevel(17)
  final val CompressionLevel18 = new ZstdCompressionLevel(18)
  final val CompressionLevel19 = new ZstdCompressionLevel(19)
  final val CompressionLevelUltra20 = new ZstdCompressionLevel(20)
  final val CompressionLevelUltra21 = new ZstdCompressionLevel(21)
  final val CompressionLevelUltra22 = new ZstdCompressionLevel(22)

  final val FastCompressionLevel: ZstdCompressionLevel = CompressionLevelFast4
  final val DefaultCompressionLevel: ZstdCompressionLevel = CompressionLevel3
  final val HighCompressionLevel: ZstdCompressionLevel = CompressionLevel6
  final val VeryHighCompressionLevel: ZstdCompressionLevel = CompressionLevel19
  final val UltraCompressionLevel: ZstdCompressionLevel = CompressionLevelUltra22
}

// The minimum level of -131072 was calculated as `-(1L << 17)`
// and was derived from the C implementation of Zstd. See:
// https://github.com/facebook/zstd/blob/b880f20d52a925ebee373b5050c206ba325d935d/lib/compress/zstd_compress.c#L6988,
// https://github.com/facebook/zstd/blob/b880f20d52a925ebee373b5050c206ba325d935d/lib/zstd.h#L1249 and
// https://github.com/facebook/zstd/blob/b880f20d52a925ebee373b5050c206ba325d935d/lib/zstd.h#L142-L143.
