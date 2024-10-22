package zio.compress

import zio.Trace
import zio.stream._

trait Decompressor extends Serializable {
  def decompress(implicit trace: Trace): ZPipeline[Any, Throwable, Byte, Byte]
}

object Decompressor {
  def empty: Decompressor = new Decompressor {
    override def decompress(implicit trace: Trace): ZPipeline[Any, Nothing, Byte, Byte] = ZPipeline.identity
  }
}
