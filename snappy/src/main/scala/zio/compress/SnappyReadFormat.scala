package zio.compress

import org.xerial.snappy.SnappyInputStream

sealed trait SnappyReadFormat extends Product with Serializable

object SnappyReadFormat {

  /** Read snappy in the framed format.
    *
    * @param verifyChecksums
    *   if `true` (the default), checksums in input stream will be verified, if `false` checksums are not verified
    * @see
    *   https://github.com/google/snappy/blob/master/framing_format.txt
    */
  final case class Framed(verifyChecksums: Boolean = true) extends SnappyReadFormat

  /** Read snappy in the basic (unframed) format.
    *
    * @param maxChunkSize
    *   the maximum expected number of bytes that were compressed together. Defaults to 512 MiB. Must be in [1, 512
    *   MiB].
    */
  final case class Basic(maxChunkSize: Int = SnappyInputStream.MAX_CHUNK_SIZE) extends SnappyReadFormat {
    require(
      1 <= maxChunkSize && maxChunkSize <= SnappyInputStream.MAX_CHUNK_SIZE,
      s"maxChunkSize must be in [1, ${SnappyInputStream.MAX_CHUNK_SIZE}], got $maxChunkSize",
    )
  }

}
