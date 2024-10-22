package zio.compress

/** Bzip2 block size.
  *
  * @param hundredKbIncrements
  *   a bzip2 block size in 100KB increments, valid values: 1 to 9
  */
final case class Bzip2BlockSize private (hundredKbIncrements: Int) extends AnyVal

object Bzip2BlockSize {

  /** Makes a bzip2 block size.
    *
    * @param hundredKbIncrements
    *   a bzip2 block size in 100KB increments, valid values: 1 to 9
    * @return
    *   a [[Bzip2BlockSize]] or `None` if the block size is not valid
    */
  def apply(hundredKbIncrements: Int): Option[Bzip2BlockSize] =
    if (1 <= hundredKbIncrements && hundredKbIncrements <= 9) Some(new Bzip2BlockSize(hundredKbIncrements)) else None

  final val BlockSize100KB = new Bzip2BlockSize(1)
  final val BlockSize200KB = new Bzip2BlockSize(2)
  final val BlockSize300KB = new Bzip2BlockSize(3)
  final val BlockSize400KB = new Bzip2BlockSize(4)
  final val BlockSize500KB = new Bzip2BlockSize(5)
  final val BlockSize600KB = new Bzip2BlockSize(6)
  final val BlockSize700KB = new Bzip2BlockSize(7)
  final val BlockSize800KB = new Bzip2BlockSize(8)
  final val BlockSize900KB = new Bzip2BlockSize(9)
}
