package zio.compress

import zio._
import zio.stream.ZStream
import zio.test._

import java.nio.charset.StandardCharsets.UTF_8

object ArchiverSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("Archiver")(
      test("checkUncompressedSize does nothing when there is no size") {
        for {
          _ <- ZStream(
                 archiveEntryNoSize("file1.txt", "Hello world!"),
                 archiveEntryNoSize("subdir/file2.txt", "Hello from subdir!"),
               )
                 .via(Archiver.checkUncompressedSize)
                 .mapZIO { case (_, stream) =>
                   stream.runDrain
                 }
                 .runCollect
        } yield assertCompletes
      },
      test("checkUncompressedSize does nothing for correct sizes") {
        for {
          _ <- ZStream(
                 archiveEntryWithSize("file1.txt", 12, "Hello world!"),
                 archiveEntryWithSize("subdir/file2.txt", 18, "Hello from subdir!"),
               )
                 .via(Archiver.checkUncompressedSize)
                 .mapZIO { case (_, stream) =>
                   stream.runDrain
                 }
                 .runCollect
                 .exit
        } yield assertCompletes
      },
      test("checkUncompressedSize finds incorrect size") {
        for {
          obtained <- ZStream(
                        archiveEntryWithSize("file1.txt", 12, "Hello world!"), // correct size
                        archiveEntryWithSize("subdir/file2.txt", 20000, "Hello from subdir!"), // incorrect size
                      )
                        .via(Archiver.checkUncompressedSize)
                        .mapZIO { case (_, stream) =>
                          stream.runDrain
                        }
                        .runDrain
                        .exit
        } yield
          assertTrue(
            obtained.is(_.die).getMessage ==
              "Entry size of 18 bytes does not match size of 20000 bytes specified in entry"
          )
      },
    )

  private def archiveEntryWithSize(
    name: String,
    size: Long,
    content: String,
  ): (ArchiveEntry[Some, Any], ZStream[Any, Throwable, Byte]) = {
    val contentBytes = content.getBytes(UTF_8)
    (ArchiveEntry(name, Some(size)), ZStream.fromIterable(contentBytes))
  }

  private def archiveEntryNoSize(
    name: String,
    content: String,
  ): (ArchiveEntry[Option, Any], ZStream[Any, Throwable, Byte]) = {
    val contentBytes = content.getBytes(UTF_8)
    (ArchiveEntry(name), ZStream.fromIterable(contentBytes))
  }

}
