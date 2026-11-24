package zio.compress

/** A Zstd compression level, used by [[ZstdCompressor]]. Make one with `ZstdCompressionLevel(level)`, which returns
  * `None` for a level out of range, or use one of the named constants. Higher levels usually give smaller output. Level
  * 0 is special: zstd treats it as its default level, 3.
  *
  * @param level
  *   compression level, valid values: -131072 to 22
  */
final case class ZstdCompressionLevel private (level: Int) extends AnyVal

/** Makes [[ZstdCompressionLevel]] values and names the levels from -7 to 22. */
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

  /** Level -7. */
  final val CompressionLevelFast7 = new ZstdCompressionLevel(-7)

  /** Level -6. */
  final val CompressionLevelFast6 = new ZstdCompressionLevel(-6)

  /** Level -5. */
  final val CompressionLevelFast5 = new ZstdCompressionLevel(-5)

  /** Level -4. */
  final val CompressionLevelFast4 = new ZstdCompressionLevel(-4)

  /** Level -3. */
  final val CompressionLevelFast3 = new ZstdCompressionLevel(-3)

  /** Level -2. */
  final val CompressionLevelFast2 = new ZstdCompressionLevel(-2)

  /** Level -1. */
  final val CompressionLevelFast1 = new ZstdCompressionLevel(-1)

  /** Level 0. zstd treats level 0 as its default level, so this gives the same output as `CompressionLevel3`. */
  final val CompressionLevelFast0 = new ZstdCompressionLevel(0)

  /** Level 1. */
  final val CompressionLevel1 = new ZstdCompressionLevel(1)

  /** Level 2. */
  final val CompressionLevel2 = new ZstdCompressionLevel(2)

  /** Level 3, the zstd default level. */
  final val CompressionLevel3 = new ZstdCompressionLevel(3)

  /** Level 4. */
  final val CompressionLevel4 = new ZstdCompressionLevel(4)

  /** Level 5. */
  final val CompressionLevel5 = new ZstdCompressionLevel(5)

  /** Level 6. */
  final val CompressionLevel6 = new ZstdCompressionLevel(6)

  /** Level 7. */
  final val CompressionLevel7 = new ZstdCompressionLevel(7)

  /** Level 8. */
  final val CompressionLevel8 = new ZstdCompressionLevel(8)

  /** Level 9. */
  final val CompressionLevel9 = new ZstdCompressionLevel(9)

  /** Level 10. */
  final val CompressionLevel10 = new ZstdCompressionLevel(10)

  /** Level 11. */
  final val CompressionLevel11 = new ZstdCompressionLevel(11)

  /** Level 12. */
  final val CompressionLevel12 = new ZstdCompressionLevel(12)

  /** Level 13. */
  final val CompressionLevel13 = new ZstdCompressionLevel(13)

  /** Level 14. */
  final val CompressionLevel14 = new ZstdCompressionLevel(14)

  /** Level 15. */
  final val CompressionLevel15 = new ZstdCompressionLevel(15)

  /** Level 16. */
  final val CompressionLevel16 = new ZstdCompressionLevel(16)

  /** Level 17. */
  final val CompressionLevel17 = new ZstdCompressionLevel(17)

  /** Level 18. */
  final val CompressionLevel18 = new ZstdCompressionLevel(18)

  /** Level 19. */
  final val CompressionLevel19 = new ZstdCompressionLevel(19)

  /** Level 20. */
  final val CompressionLevelUltra20 = new ZstdCompressionLevel(20)

  /** Level 21. */
  final val CompressionLevelUltra21 = new ZstdCompressionLevel(21)

  /** Level 22. */
  final val CompressionLevelUltra22 = new ZstdCompressionLevel(22)

  /** A fast level: `CompressionLevelFast4`, level -4. */
  final val FastCompressionLevel: ZstdCompressionLevel = CompressionLevelFast4

  /** The zstd default level: `CompressionLevel3`, level 3. */
  final val DefaultCompressionLevel: ZstdCompressionLevel = CompressionLevel3

  /** A high level: `CompressionLevel6`, level 6. */
  final val HighCompressionLevel: ZstdCompressionLevel = CompressionLevel6

  /** A very high level: `CompressionLevel19`, level 19. */
  final val VeryHighCompressionLevel: ZstdCompressionLevel = CompressionLevel19

  /** The highest level: `CompressionLevelUltra22`, level 22. */
  final val UltraCompressionLevel: ZstdCompressionLevel = CompressionLevelUltra22
}

// The minimum level of -131072 was calculated as `-(1L << 17)`
// and was derived from the C implementation of Zstd. See:
// https://github.com/facebook/zstd/blob/b880f20d52a925ebee373b5050c206ba325d935d/lib/compress/zstd_compress.c#L6988,
// https://github.com/facebook/zstd/blob/b880f20d52a925ebee373b5050c206ba325d935d/lib/zstd.h#L1249 and
// https://github.com/facebook/zstd/blob/b880f20d52a925ebee373b5050c206ba325d935d/lib/zstd.h#L142-L143.
