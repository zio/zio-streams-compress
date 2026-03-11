package zio.compress

import zio.Trace
import zio.stream._

/** An unarchiver makes pipelines that accept the byte stream of an archive, and produce a stream of archive entries.
  *
  * @tparam Size
  *   Either a `Some` when the archive entries have a known uncompressed size, `None` when the archive entries _do not_
  *   have the uncompressed size, or `Option` when the archive entries _might_ have the uncompressed size.
  * @tparam Underlying
  *   The archive entries from the underlying archiving library.
  */
trait Unarchiver[Size[A] <: Option[A], Underlying] extends Serializable {

  /** Make a pipeline that accepts the byte stream of an archive, and produces a stream of archive entries with accurate
    * uncompressed size.
    */
  def list(implicit
    trace: Trace
  ): ZPipeline[Any, Throwable, Byte, ArchiveEntry[Some, Underlying]] =
    unarchive.mapZIO { case (entry, content) => content.runCount.map(c => entry.withKnownUncompressedSize(c)) }

  /** Make a pipeline that accepts the byte stream of an archive, and produces a stream of archive entries and their
    * content.
    *
    * ⚠️Note: the entry's content must be fully read before the next archive entry is emitted. See the project's README
    * for the consequences, tips and workarounds.
    */
  def unarchive(implicit
    trace: Trace
  ): ZPipeline[Any, Throwable, Byte, (ArchiveEntry[Size, Underlying], ZStream[Any, Throwable, Byte])]
}
