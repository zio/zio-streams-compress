package zio.compress

import zio.stream._
import zio.Trace

trait Compressor extends Serializable {
  def compress(implicit trace: Trace): ZPipeline[Any, Throwable, Byte, Byte]
}

object Compressor {
  def empty: Compressor = new Compressor {
    override def compress(implicit trace: Trace): ZPipeline[Any, Nothing, Byte, Byte] = ZPipeline.identity
  }
}
