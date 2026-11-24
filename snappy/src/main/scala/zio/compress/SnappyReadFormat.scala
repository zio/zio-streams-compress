package zio.compress

import org.xerial.snappy.SnappyInputStream

/** The Snappy format that `SnappyDecompressor` expects: `Framed` or `Basic`. */
sealed trait SnappyReadFormat extends Product with Serializable

/** The two Snappy read formats of `SnappyDecompressor`. */
object SnappyReadFormat {

  /** Read snappy in the framed format.
    *
    * @param verifyChecksums
    *   if `true` (the default), checksums in input stream will be verified, if `false` checksums are not verified. A
    *   checksum that does not match fails the stream with a `java.io.IOException` (`Corrupt input: invalid checksum`).
    * @see
    *   [[https://github.com/google/snappy/blob/main/framing_format.txt Snappy framing format]]
    */
  final case class Framed(verifyChecksums: Boolean = true) extends SnappyReadFormat

  /** Read snappy in the basic (unframed) format: the stream format of snappy-java's `SnappyOutputStream`, as written by
    * `SnappyWriteFormat.Basic`. It starts with a snappy-java header and is not the Snappy framing format.
    *
    * Throws a `java.lang.IllegalArgumentException` when `maxChunkSize` is out of range, for example
    * `requirement failed: maxChunkSize must be in [1, 536870912], got 0`.
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
