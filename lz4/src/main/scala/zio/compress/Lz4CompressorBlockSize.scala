package zio.compress

sealed trait Lz4CompressorBlockSize extends Product with Serializable

object Lz4CompressorBlockSize {
  case object BlockSize64KiB extends Lz4CompressorBlockSize
  case object BlockSize256KiB extends Lz4CompressorBlockSize
  case object BlockSize1MiB extends Lz4CompressorBlockSize
  case object BlockSize4MiB extends Lz4CompressorBlockSize

  /** Converts a Lz4 block size indicator into a [[Lz4CompressorBlockSize]].
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
