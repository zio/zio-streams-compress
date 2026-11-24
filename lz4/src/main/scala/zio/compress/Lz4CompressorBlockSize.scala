package zio.compress

/** The maximum size of the blocks that `Lz4Compressor` compresses one at a time: 64 KiB, 256 KiB, 1 MiB or 4 MiB. */
sealed trait Lz4CompressorBlockSize extends Product with Serializable

/** The four LZ4 block sizes, and `fromLz4BlockSizeIndicator` to look one up by its number in the LZ4 format. */
object Lz4CompressorBlockSize {

  /** Blocks of up to 64 KiB (block size indicator 4). */
  case object BlockSize64KiB extends Lz4CompressorBlockSize

  /** Blocks of up to 256 KiB (block size indicator 5). The default of `Lz4Compressor`. */
  case object BlockSize256KiB extends Lz4CompressorBlockSize

  /** Blocks of up to 1 MiB (block size indicator 6). */
  case object BlockSize1MiB extends Lz4CompressorBlockSize

  /** Blocks of up to 4 MiB (block size indicator 7). */
  case object BlockSize4MiB extends Lz4CompressorBlockSize

  /** Converts a Lz4 block size indicator into a [[Lz4CompressorBlockSize]], or `None` for any other number.
    *
    * @param indicator
    *   the Lz4 block size indicator, valid values: 4 (64KiB), 5 (256KiB), 6 (1MiB), 7 (4MiB)
    */
  def fromLz4BlockSizeIndicator(indicator: Int): Option[Lz4CompressorBlockSize] =
    indicator match {
      case 4 => Some(BlockSize64KiB)
      case 5 => Some(BlockSize256KiB)
      case 6 => Some(BlockSize1MiB)
      case 7 => Some(BlockSize4MiB)
      case _ => None
    }
}
