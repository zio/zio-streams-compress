package zio.compress

sealed abstract class Bzip2BlockSize(val jValue: Int)

object Bzip2BlockSize {
  case object BlockSize100KB extends Bzip2BlockSize(1)
  case object BlockSize200KB extends Bzip2BlockSize(2)
  case object BlockSize300KB extends Bzip2BlockSize(3)
  case object BlockSize400KB extends Bzip2BlockSize(4)
  case object BlockSize500KB extends Bzip2BlockSize(5)
  case object BlockSize600KB extends Bzip2BlockSize(6)
  case object BlockSize700KB extends Bzip2BlockSize(7)
  case object BlockSize800KB extends Bzip2BlockSize(8)
  case object BlockSize900KB extends Bzip2BlockSize(9)

  private val Values: Seq[Bzip2BlockSize] =
    Seq(
      BlockSize100KB,
      BlockSize200KB,
      BlockSize300KB,
      BlockSize400KB,
      BlockSize500KB,
      BlockSize600KB,
      BlockSize700KB,
      BlockSize800KB,
      BlockSize900KB,
    )

  /** Converts a bzip2 block size from `Int` to [[Bzip2BlockSize]].
    *
    * @param blockSize100KB
    *   a bzip2 block size in 100KB increments, valid values: 1 to 9
    */
  def fromBzip2BlockSize(blockSize100KB: Int): Option[Bzip2BlockSize] =
    Values.find(_.jValue == blockSize100KB)
}
