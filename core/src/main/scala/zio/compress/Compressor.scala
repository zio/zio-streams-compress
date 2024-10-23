package zio.compress

import zio.stream._
import zio.Trace

trait Compressor extends Serializable {

  /** A pipeline that takes a raw byte stream and produces a compressed byte stream. */
  def compress(implicit trace: Trace): ZPipeline[Any, Throwable, Byte, Byte]
}

object Compressor extends Serializable {

  /** A compressor that does nothing; it passes all bytes through unchanged. */
  def empty: Compressor = new Compressor {

    override def compress(implicit trace: Trace): ZPipeline[Any, Nothing, Byte, Byte] =
      ZPipeline.identity
  }
}
