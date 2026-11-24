package zio.compress

import zio.Chunk

/** Deflate compression level, used for ZIP and GZIP.
  *
  * @param level
  *   compression level, valid values: 0 (no compression) to 9 (maximum compression)
  */
final case class DeflateCompressionLevel private (level: Int) extends AnyVal

/** The ten deflate compression levels, and `apply` to look one up by number. */
object DeflateCompressionLevel {

  /** Returns the deflate compression level `level`, or `None` when `level` is outside 0 to 9.
    *
    * @param level
    *   compression level, valid values: 0 (no compression) to 9 (maximum compression)
    */
  def apply(level: Int): Option[DeflateCompressionLevel] =
    if (0 <= level && level <= 9) Some(new DeflateCompressionLevel(level)) else None

  /** Deflate compression level 0, which stores the data without compressing it. */
  final val CompressionLevel0 = new DeflateCompressionLevel(0)

  /** Deflate compression level 1. */
  final val CompressionLevel1 = new DeflateCompressionLevel(1)

  /** Deflate compression level 2. */
  final val CompressionLevel2 = new DeflateCompressionLevel(2)

  /** Deflate compression level 3. */
  final val CompressionLevel3 = new DeflateCompressionLevel(3)

  /** Deflate compression level 4. */
  final val CompressionLevel4 = new DeflateCompressionLevel(4)

  /** Deflate compression level 5. */
  final val CompressionLevel5 = new DeflateCompressionLevel(5)

  /** Deflate compression level 6. */
  final val CompressionLevel6 = new DeflateCompressionLevel(6)

  /** Deflate compression level 7. */
  final val CompressionLevel7 = new DeflateCompressionLevel(7)

  /** Deflate compression level 8. */
  final val CompressionLevel8 = new DeflateCompressionLevel(8)

  /** Deflate compression level 9, the highest level. */
  final val CompressionLevel9 = new DeflateCompressionLevel(9)

  /** Level 0, which stores the data without compressing it. */
  final val NoCompressionLevel: DeflateCompressionLevel = CompressionLevel0

  /** Level 1, the fastest level that compresses. */
  final val FastestCompressionLevel: DeflateCompressionLevel = CompressionLevel1

  /** Level 5. Leaving the level unset (`None`) in `ZipArchiver` or `GzipCompressor` is not the same: it uses zlib's
    * default level, which is 6.
    */
  final val DefaultCompressionLevel: DeflateCompressionLevel = CompressionLevel5

  /** Level 9, the highest level. */
  final val BestCompressionLevel: DeflateCompressionLevel = CompressionLevel9

  /** All ten levels, from `CompressionLevel0` to `CompressionLevel9`, in that order. */
  final val Values: Chunk[DeflateCompressionLevel] =
    Chunk(
      CompressionLevel0,
      CompressionLevel1,
      CompressionLevel2,
      CompressionLevel3,
      CompressionLevel4,
      CompressionLevel5,
      CompressionLevel6,
      CompressionLevel7,
      CompressionLevel8,
      CompressionLevel9,
    )

}
