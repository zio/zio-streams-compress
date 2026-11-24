package zio.compress

import org.xerial.snappy.SnappyFramedOutputStream

/** The Snappy format that `SnappyCompressor` writes: `Framed`, `Basic` or `HadoopCompatible`. */
sealed trait SnappyWriteFormat extends Product with Serializable

/** The three Snappy write formats of `SnappyCompressor`. */
object SnappyWriteFormat {
  private val DefaultBasicBlockSize = 32 * 1024
  private val MinBasicBlockSize = 1 * 1024
  private val MaxBasicBlockSize = 512 * 1024 * 1024 // 512 MiB

  /** Write snappy in the framed format.
    *
    * Throws a `java.lang.IllegalArgumentException` when an option is out of range, for example
    * `requirement failed: blockSize must be in [1, 65536], got 70000`.
    *
    * @param blockSize
    *   the number of bytes from the input that are compressed together. Larger blocks usually give smaller output.
    *   Defaults to 64 KiB. Must be in [1, 64 KiB].
    * @param minCompressionRatio
    *   the highest ratio of compressed length to raw length (`compressedLength / rawLength`) at which a block is
    *   written compressed; a block that compresses less well is written uncompressed. Defaults to 0.85. Must be in
    *   (0.0, 1.0].
    * @see
    *   [[https://github.com/google/snappy/blob/main/framing_format.txt Snappy framing format]]
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

  /** Write snappy in the basic (unframed) format: the stream format of snappy-java's `SnappyOutputStream`, which starts
    * with a snappy-java header and is not the Snappy framing format. Read it with `SnappyReadFormat.Basic`.
    *
    * Throws a `java.lang.IllegalArgumentException` when `blockSize` is out of range, for example
    * `requirement failed: blockSize must be in [1024 and 536870912], got 100`.
    *
    * @param blockSize
    *   the number of bytes from the input that are compressed together. Larger blocks usually give smaller output.
    *   Defaults to 32 KiB. Must be in [1 KiB, 512 MiB].
    */
  final case class Basic(blockSize: Int = DefaultBasicBlockSize) extends SnappyWriteFormat {
    require(
      MinBasicBlockSize <= blockSize && blockSize <= MaxBasicBlockSize,
      s"blockSize must be in [$MinBasicBlockSize and $MaxBasicBlockSize], got $blockSize",
    )
  }

  /** Write snappy in the format used by Hadoop and Spark.
    *
    * Compression for use with Hadoop libraries: it does not emit a file header but writes out the current block size as
    * a preamble to each block. `SnappyDecompressor` has no read format for it.
    *
    * Throws a `java.lang.IllegalArgumentException` when `blockSize` is out of range, with the same message as `Basic`.
    *
    * @param blockSize
    *   the number of bytes from the input that are compressed together. Larger blocks usually give smaller output.
    *   Defaults to 32 KiB. Must be in [1 KiB, 512 MiB].
    */
  final case class HadoopCompatible(blockSize: Int = DefaultBasicBlockSize) extends SnappyWriteFormat {
    require(
      MinBasicBlockSize <= blockSize && blockSize <= MaxBasicBlockSize,
      s"blockSize must be in [$MinBasicBlockSize and $MaxBasicBlockSize], got $blockSize",
    )
  }
}
