package zio.compress

import zio.Trace
import zio.stream._

trait Decompressor extends Serializable {

  /** A pipeline that decompresses a byte stream to an uncompressed byte stream. */
  def decompress(implicit trace: Trace): ZPipeline[Any, Throwable, Byte, Byte]
}

object Decompressor {

  /** A decompressor that does nothing; it passes all bytes through unchanged. */
  def empty: Decompressor = new Decompressor {

    override def decompress(implicit trace: Trace): ZPipeline[Any, Nothing, Byte, Byte] =
      ZPipeline.identity
  }
}
