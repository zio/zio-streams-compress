package zio.compress

import zio._
import zio.stream.{ZPipeline, ZStream}

/** An archiver makes pipelines that accept a stream of archive entries, and produce a byte stream of an archive.
  *
  * Each element of the input is a pair of an [[ArchiveEntry]] and the content stream of that entry. The zip module
  * provides `ZipArchiver`, the zip4j module `Zip4JArchiver` and the tar module `TarArchiver`; each one reads the
  * content stream of an entry to its end before it takes the next entry. Because `Size` is contravariant, an
  * `Archiver[Option]` is also an `Archiver[Some]`: code that takes an `Archiver[Some]` and gives every entry a known
  * size works with all three.
  *
  * {{{
  * import zio.compress._
  * import zio.stream._
  *
  * def archiveTexts(archiver: Archiver[Some], files: Map[String, String]): ZStream[Any, Throwable, Byte] =
  *   ZStream
  *     .fromIterable(files.toList)
  *     .map { case (name, text) =>
  *       val content = text.getBytes("UTF-8")
  *       val entry   = ArchiveEntry[Option, Any](name).withKnownUncompressedSize(content.length.toLong)
  *       (entry, ZStream.fromIterable(content))
  *     }
  *     .via(archiver.archive)
  * }}}
  *
  * @tparam Size
  *   Either a `Some` when the archive entries require the uncompressed size, or `Option` when the archive entries do
  *   not require the uncompressed size.
  * @see
  *   [[https://zio.dev/zio-streams-compress/reference-core/ ZIO Streams Compress: core reference]]
  */
trait Archiver[-Size[A] <: Option[A]] extends Serializable {

  /** Makes a pipeline that accepts a stream of archive entries, and produces a byte stream of an archive. */
  def archive(implicit
    trace: Trace
  ): ZPipeline[Any, Throwable, (ArchiveEntry[Size, Any], ZStream[Any, Throwable, Byte]), Byte]
}

/** Companion of [[Archiver]], with a helper for archiver implementations. */
object Archiver {

  /** Returns a pipeline that passes each entry through unchanged, except that it checks the length of the entry's
    * content stream against the entry's `uncompressedSize`. When the number of bytes differs, the content stream dies,
    * after its last byte, with a `java.lang.IllegalStateException`. Its message gives the counted bytes first and the
    * declared size second, for example: `Entry size of 5 bytes does not match size of 3 bytes specified in entry`.
    *
    * Entries without a size pass unchecked. `TarArchiver` and `Zip4JArchiver` apply this check to their input.
    */
  def checkUncompressedSize[Size[A] <: Option[A]]: ZPipeline[
    Any,
    Throwable,
    (ArchiveEntry[Size, Any], ZStream[Any, Throwable, Byte]),
    (ArchiveEntry[Size, Any], ZStream[Any, Throwable, Byte]),
  ] =
    ZPipeline.fromFunction(
      _.map { case (entry, byteStream) =>
        val newByteStream = (entry.uncompressedSize: Option[Long]) match {
          case None =>
            byteStream
          case Some(expectedSize: Long) =>
            ZStream.unwrap {
              Ref.make(0L).map { sizeRef =>
                byteStream
                  .chunks
                  .tap(chunk => sizeRef.update(_ + chunk.size))
                  .flattenChunks ++
                  ZStream.unwrap {
                    sizeRef.get.map { size =>
                      if (size == expectedSize) ZStream.empty
                      else
                        ZStream.fail(
                          throw new IllegalStateException(
                            s"Entry size of $size bytes does not match size of $expectedSize bytes specified in entry"
                          )
                        )
                    }
                  }
              }
            }
        }

        (entry, newByteStream)
      }
    )

}
