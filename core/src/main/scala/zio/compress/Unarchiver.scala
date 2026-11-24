package zio.compress

import zio.Trace
import zio.stream._

/** An unarchiver makes pipelines that accept the byte stream of an archive, and produce a stream of archive entries.
  *
  * The zip module provides `ZipUnarchiver`, the zip4j module `Zip4JUnarchiver` and the tar module `TarUnarchiver`.
  *
  * @tparam Size
  *   Either a `Some` when the archive entries have a known uncompressed size, `None` when the archive entries _do not_
  *   have the uncompressed size, or `Option` when the archive entries _might_ have the uncompressed size.
  * @tparam Underlying
  *   The archive entries from the underlying archiving library.
  * @see
  *   [[https://zio.dev/zio-streams-compress/reference-core/ ZIO Streams Compress: core reference]]
  */
trait Unarchiver[Size[A] <: Option[A], Underlying] extends Serializable {

  /** Makes a pipeline that accepts the byte stream of an archive, and produces a stream of its archive entries without
    * their content. It reads the content of each entry to count its bytes, and sets that count as the entry's
    * uncompressed size, also for entries whose header gives no size.
    */
  def list(implicit
    trace: Trace
  ): ZPipeline[Any, Throwable, Byte, ArchiveEntry[Some, Underlying]] =
    unarchive.mapZIO { case (entry, content) => content.runCount.map(c => entry.withKnownUncompressedSize(c)) }

  /** Makes a pipeline that accepts the byte stream of an archive, and produces a stream of archive entries and their
    * content.
    *
    * ⚠️Note: the entry's content must be fully read before the next archive entry is emitted. Until then the pipeline
    * waits: when a content stream is not read, or read only in part, it waits forever. Read the content inside the step
    * that handles the entry, for example in `mapZIO`.
    */
  def unarchive(implicit
    trace: Trace
  ): ZPipeline[Any, Throwable, Byte, (ArchiveEntry[Size, Underlying], ZStream[Any, Throwable, Byte])]
}
