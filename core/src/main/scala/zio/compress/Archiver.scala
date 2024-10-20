package zio.compress

import zio._
import zio.stream.{ZPipeline, ZStream}

trait Archiver[Size[A] <: Option[A]] {
  def archive: ZPipeline[Any, Throwable, (ArchiveEntry[Size, Any], ZStream[Any, Throwable, Byte]), Byte]
}

object Archiver {

  /**
   * @return
   *   a pipeline that checks if the uncompressed size of the entries match the size specified in the entry header
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
