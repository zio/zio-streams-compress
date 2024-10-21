package zio.compress

/** Bzip2 block size.
  *
  * @param hundredKbIncrements
  *   a bzip2 block size in 100KB increments, valid values: 1 to 9
  */
final case class Bzip2BlockSize private (hundredKbIncrements: Int)

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

  val BlockSize100KB = new Bzip2BlockSize(1)
  val BlockSize200KB = new Bzip2BlockSize(2)
  val BlockSize300KB = new Bzip2BlockSize(3)
  val BlockSize400KB = new Bzip2BlockSize(4)
  val BlockSize500KB = new Bzip2BlockSize(5)
  val BlockSize600KB = new Bzip2BlockSize(6)
  val BlockSize700KB = new Bzip2BlockSize(7)
  val BlockSize800KB = new Bzip2BlockSize(8)
  val BlockSize900KB = new Bzip2BlockSize(9)
}
