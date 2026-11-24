package zio.compress

/** A Bzip2 block size, in steps of 100KB (100,000 bytes), used by [[Bzip2Compressor]]. Make one with
  * `Bzip2BlockSize(hundredKbIncrements)`, which returns `None` for a value out of range, or use one of the named
  * constants.
  *
  * @param hundredKbIncrements
  *   a bzip2 block size in 100KB increments, valid values: 1 to 9
  */
final case class Bzip2BlockSize private (hundredKbIncrements: Int) extends AnyVal

/** Makes [[Bzip2BlockSize]] values and names each block size from 100KB to 900KB. */
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

  /** Block size 1: 100KB. */
  final val BlockSize100KB = new Bzip2BlockSize(1)

  /** Block size 2: 200KB. */
  final val BlockSize200KB = new Bzip2BlockSize(2)

  /** Block size 3: 300KB. */
  final val BlockSize300KB = new Bzip2BlockSize(3)

  /** Block size 4: 400KB. */
  final val BlockSize400KB = new Bzip2BlockSize(4)

  /** Block size 5: 500KB. */
  final val BlockSize500KB = new Bzip2BlockSize(5)

  /** Block size 6: 600KB. */
  final val BlockSize600KB = new Bzip2BlockSize(6)

  /** Block size 7: 700KB. */
  final val BlockSize700KB = new Bzip2BlockSize(7)

  /** Block size 8: 800KB. */
  final val BlockSize800KB = new Bzip2BlockSize(8)

  /** Block size 9: 900KB, the largest block size and the default of `Bzip2Compressor`. */
  final val BlockSize900KB = new Bzip2BlockSize(9)
}
