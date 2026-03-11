package zio.compress

import zio._
import zio.compress.ArchiveEntry.ArchiveEntryFromUnderlying
import zio.stream.{ZPipeline, ZStream}
import zio.test._

object UnarchiverSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("Unarchiver")(
      test("list reads full content") {
        for {
          result <- ZStream[Byte](5, 10, 3)
                      .via(new TestUnarchiver().list)
                      .runCollect
        } yield {
          val entryNames = result.map(_.name)
          val entrySizes = result.flatMap(_.uncompressedSize)
          assertTrue(
            entryNames == Chunk("entry 5", "entry 10", "entry 3"),
            entrySizes == Chunk(5L, 10L, 3L),
          )
        }
      }
    )

  // A test unarchiver which for every byte in the input generates a fake entry with fake contents.
  // Waits with emitting an entry until the content of the previous entry has been fully read.

  private type PipelineState = Option[Promise[Nothing, Unit]]
  private type PipelineOut = (ArchiveEntry[Some, String], ZStream[Any, Throwable, Byte])

  private class TestUnarchiver extends Unarchiver[Some, String] {
    override def unarchive(implicit
      trace: Trace
    ): ZPipeline[Any, Throwable, Byte, PipelineOut] =
      ZPipeline.rechunk(1) >>>
        // Unlike 2.12 and 2.13, scala 3.3 has problems deriving the `In` type, we have to write it out:
        ZPipeline.mapAccumZIO[Any, Throwable, Byte, PipelineState, PipelineOut](None) {
          case (previousEntryFullyRead, byte) =>
            for {
              _ <- previousEntryFullyRead.fold(ZIO.unit)(_.await)
              entryFullyRead <- Promise.make[Nothing, Unit]
            } yield {
              val archiveEntry = ArchiveEntry.fromUnderlying[Option, String](s"entry $byte")
                .withKnownUncompressedSize(byte.toLong)
              val fakeEntryContent: Chunk[Byte] = Chunk.tabulate(byte.toInt)(_.toByte)
              val entryContentStream = ZStream.fromChunk(fakeEntryContent) ++
                ZStream.execute(entryFullyRead.succeed(()))
              (Some(entryFullyRead), (archiveEntry, entryContentStream))
            }
        }
  }

  // noinspection ConvertExpressionToSAM
  private implicit val testArchiveEntryFromUnderlying: ArchiveEntryFromUnderlying[Option, String] =
    new ArchiveEntryFromUnderlying[Option, String] {
      override def archiveEntry(underlying: String): ArchiveEntry[Option, String] =
        ArchiveEntry(
          name = underlying,
          isDirectory = false,
          uncompressedSize = Option.empty[Long],
          lastModified = None,
          lastAccess = None,
          creation = None,
          underlying = underlying,
        )
    }

}
