import zio._
import zio.compress.{ArchiveEntry, GzipCompressor, GzipDecompressor, TarUnarchiver, ZipArchiver}
import zio.stream._

import java.nio.charset.StandardCharsets.UTF_8

object ExampleApp extends ZIOAppDefault {
  override def run: ZIO[Any, Any, Any] =
    for {
      // Compress a file with GZIP
      _ <- ZStream
             .fromFileName("file")
             .via(GzipCompressor.make().compress)
             .run(ZSink.fromFileName("file.gz"))

      // List all items in a gzip tar archive:
      _ <- ZStream
             .fromFileName("file.tgz")
             .via(GzipDecompressor.make().decompress)
             .via(TarUnarchiver.make().unarchive)
             .mapZIO { case (archiveEntry, contentStream) =>
               for {
                 content <- contentStream.runCollect
                 _ <- Console.printLine(s"${archiveEntry.name} ${content.length}")
               } yield ()
             }
             .runDrain

      // Create a ZIP archive (use the zip4j version for password support)
      _ <- ZStream(archiveEntry("file1.txt", "Hello world!".getBytes(UTF_8)))
             .via(ZipArchiver.make().archive)
             .run(ZSink.fromFileName("file.zip"))
    } yield ()

  private def archiveEntry(
    name: String,
    content: Array[Byte],
  ): (ArchiveEntry[Some, Any], ZStream[Any, Throwable, Byte]) =
    (ArchiveEntry(name, Some(content.length.toLong)), ZStream.fromIterable(content))

}
