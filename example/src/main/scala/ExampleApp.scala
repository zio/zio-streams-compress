import zio._
import zio.compress._
import zio.stream._

import java.nio.file.{Files, Path}
import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}

object ExampleApp extends ZIOAppDefault {

  private def extractFromFile(sourceZip: Path, destinationDir: Path): ZIO[Any, Throwable, Unit] =
    for {
      _ <- ZIO.logInfo(s"[streaming] extract start $sourceZip from $sourceZip")
      entryCount = new AtomicInteger(0)
      _ <- ZStream.fromPath(sourceZip)
             .via(ZipUnarchiver.unarchive)
             .mapZIO { case (entry, contentStream) =>
               val i = entryCount.incrementAndGet()
               val targetPath = destinationDir.resolve(entry.name)
               val bytesRead = new AtomicLong(0)
               val tappedStream = contentStream.tapChunks { chunk =>
                 ZIO.succeed(bytesRead.addAndGet(chunk.size.toLong)).unit
               }
               ZIO.logInfo(
                 s"[streaming] entry #$i START name=${entry.name} isDir=${entry.isDirectory} " +
                   s"size=${entry.uncompressedSize} ($sourceZip)"
               ) *>
                 (if (entry.isDirectory) {
                    tappedStream.runDrain *>
                      ZIO.attemptBlockingIO(Files.createDirectories(targetPath))
                  } else {
                    ZIO.attemptBlockingIO(Files.createDirectories(targetPath.getParent)) *>
                      tappedStream.run(ZSink.fromPath(targetPath)).unit
                  }) *>
                 ZIO.logInfo(s"[streaming] entry #$i DONE name=${entry.name} bytesRead=${bytesRead.get()} ($sourceZip)")
             }
             .runDrain
      _ <- ZIO.logInfo(s"[streaming] extracted $sourceZip to $destinationDir entries=${entryCount.get()}")
    } yield ()

  override def run: ZIO[Any, Any, Any] = {
    // download problematic file from
    // https://repo1.maven.org/maven2/io/ktor/ktor-serialization-jvm/3.2.3/ktor-serialization-jvm-3.2.3-javadoc.jar
    extractFromFile(
      Path.of("ktor-serialization-jvm-3.2.3-javadoc.jar"),
      Files.createTempDirectory("zio-streams-compress-test")
    )
  }

}
