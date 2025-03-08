package zio.compress

import org.xerial.snappy.SnappyFramedOutputStream

sealed trait SnappyWriteFormat extends Product with Serializable

object SnappyWriteFormat {
  private val DefaultBasicBlockSize = 32 * 1024
  private val MinBasicBlockSize = 1 * 1024
  private val MaxBasicBlockSize = 512 * 1024 * 1024 // 512 MiB

  /** Write snappy in the framed format.
    *
    * @param blockSize
    *   the number of bytes from the input that are compressed together. Higher block sizes lead to higher compression
    *   ratios. Defaults to 64 KiB. Must be in [1, 64 KiB].
    * @param minCompressionRatio
    *   Defines the minimum compression ratio (`compressedLength / rawLength`) that must be achieved to write the
    *   compressed data. Defaults to 0.85. Must be in (0.0, 1.0].
    * @see
    *   https://github.com/google/snappy/blob/master/framing_format.txt
    */
  final case class Framed(
    blockSize: Int = SnappyFramedOutputStream.DEFAULT_BLOCK_SIZE,
    minCompressionRatio: Double = SnappyFramedOutputStream.DEFAULT_MIN_COMPRESSION_RATIO,
  ) extends SnappyWriteFormat {
    require(
      1 <= blockSize && blockSize <= SnappyFramedOutputStream.MAX_BLOCK_SIZE,
      s"blockSize must be in [1, ${SnappyFramedOutputStream.MAX_BLOCK_SIZE}], got $blockSize",
    )
    require(
      0 < minCompressionRatio && minCompressionRatio <= 1.0,
      s"minCompressionRatio must be in (0.0, 1.0], got $minCompressionRatio",
    )
  }

  /** Write snappy in the basic (unframed) format.
    *
    * @param blockSize
    *   the number of bytes from the input that are compressed together. Higher block sizes lead to higher compression
    *   ratios. Defaults to 32 KiB. Must be in [1 KiB, 512 MiB].
    */
  final case class Basic(blockSize: Int = DefaultBasicBlockSize) extends SnappyWriteFormat {
    require(
      MinBasicBlockSize <= blockSize && blockSize <= MaxBasicBlockSize,
      s"blockSize must be in [$MinBasicBlockSize and $MaxBasicBlockSize], got $blockSize",
    )
  }

  /** Write snappy in the format used by Hadoop and Spark.
    *
    * Compression for use with Hadoop libraries: it does not emit a file header but write out the current block size as
    * a preamble to each block.
    *
    * @param blockSize
    *   the number of bytes from the input that are compressed together. Higher block sizes lead to higher compression
    *   ratios. Defaults to 32 KiB. Must be in [1 KiB, 512 MiB].
    */
  final case class HadoopCompatible(blockSize: Int = DefaultBasicBlockSize) extends SnappyWriteFormat {
    require(
      MinBasicBlockSize <= blockSize && blockSize <= MaxBasicBlockSize,
      s"blockSize must be in [$MinBasicBlockSize and $MaxBasicBlockSize], got $blockSize",
    )
  }
}
