package zio.compress

import zio.Trace
import zio.stream._

trait Unarchiver[Size[A] <: Option[A], Underlying] extends Serializable {
  def unarchive(implicit
    trace: Trace
  ): ZPipeline[Any, Throwable, Byte, (ArchiveEntry[Size, Underlying], ZStream[Any, Throwable, Byte])]
}
