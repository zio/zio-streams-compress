package zio.compress

import zio._
import zio.compress.zip.ZipMethod
import zio.compress.Zip.zipArchiveEntryToUnderlying
import zio.process.Command
import zio.stream._
import zio.test._

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64
import java.util.zip.ZipEntry

object ZipSpec extends ZIOSpecDefault {
  /* Created on an Ubuntu system with:
   * {{{
   *   mkdir ziptempdir
   *   cd ziptempdir
   *   echo -n 'Hello world!' > file1.txt
   *   mkdir -p subdir
   *   echo -n 'Hello from subdir!' > subdir/file2.txt
   *   zip - file1.txt subdir/file2.txt | base64; echo
   *   cd ..
   *   rm -rf ziptempdir
   * }}}
   */
  private final val zipArchive = Chunk.fromArray(
    Base64
      .getDecoder
      .decode(
        "UEsDBBQACAAIAC11SlkAAAAAAAAAAAwAAAAJABwAZmlsZTEudHh0VVQJAAN2ywdndssHZ3V4CwAB" +
          "BOgDAAAE6AMAAPNIzcnJVyjPL8pJUQQAUEsHCJUZhRsOAAAADAAAAFBLAwQUAAgACAAtdUpZAAAA" +
          "AAAAAAASAAAAEAAcAHN1YmRpci9maWxlMi50eHRVVAkAA3bLB2d2ywdndXgLAAEE6AMAAAToAwAA" +
          "80jNyclXSCvKz1UoLk1KySxSBABQSwcIe/g7bxQAAAASAAAAUEsBAh4DFAAIAAgALXVKWZUZhRsO" +
          "AAAADAAAAAkAGAAAAAAAAQAAALSBAAAAAGZpbGUxLnR4dFVUBQADdssHZ3V4CwABBOgDAAAE6AMA" +
          "AFBLAQIeAxQACAAIAC11Sll7+DtvFAAAABIAAAAQABgAAAAAAAEAAAC0gWEAAABzdWJkaXIvZmls" +
          "ZTIudHh0VVQFAAN2ywdndXgLAAEE6AMAAAToAwAAUEsFBgAAAAACAAIApQAAAM8AAAAAAA=="
      )
  )

  private val file1Name = "file1.txt"
  private val file1Content = "Hello world!"
  private val file2Name = "subdir/file2.txt"
  private val file2Content = "Hello from subdir!"

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("Zip")(
      test("zip unarchive") {
        for {
          obtained <- ZStream
                        .fromChunk(zipArchive)
                        .via(ZipUnarchiver.unarchive)
                        .mapZIO { case (archiveEntry, stream) =>
                          for {
                            content <- stream.runCollect
                          } yield archiveEntry.name -> new String(content.toArray)
                        }
                        .runCollect
        } yield
          assertTrue(
            obtained.head == t("file1.txt", "Hello world!"),
            obtained(1) == t("subdir/file2.txt", "Hello from subdir!"),
          )
      },
      test("zip round trip") {
        checkN(400)(
          Gen.int(40, 5000),
          Gen.chunkOfBounded(0, 20000)(Gen.byte),
          Gen.option(Gen.fromIterable(DeflateCompressionLevel.Values)),
          Gen.fromIterable(zip.ZipMethod.Values),
        ) { (chunkSize, genBytes, level, zipMethod) =>
          for {
            obtained <-
              ZStream
                .fromChunk(genBytes)
                .rechunk(chunkSize)
                .via(ArchiveSingleFileCompressor.forName(
                  ZipArchiver(level, zipMethod),
                  "test",
                  genBytes.length.toLong,
                ).compress)
                .via(ArchiveSingleFileDecompressor(ZipUnarchiver()).decompress)
                .runCollect
          } yield assertTrue(obtained == genBytes)
        }
      },
      test("zip compress without entry size") {
        for {
          obtained <-
            ZStream(archiveEntry("readme.txt", "Hello world!"))
              .via(ZipArchiver.archive)
              .via(ZipUnarchiver.unarchive)
              // The unarchiver doesn't end nor produce more entries unless the entry's content is fully read
              .tap { case (_, stream) => stream.runDrain }
              .runCollect
        } yield assertTrue(obtained.head._1.name == "readme.txt")
      },
      test("copy zip entry") {
        val entry = ArchiveEntry(name = "test")
        val zipEntry = entry.withUnderlying(entry.underlying[ZipEntry])
        val zipEntry2 = zipEntry.withName("test2")
        val zipArchiveEntry = zipEntry2.underlying[ZipEntry] // underlying ZipArchiveEntry entry is copied
        assertTrue(zipArchiveEntry.getName == "test2")
      },
      test("deflated Zip file contains deflated entries") {
        for {
          entries <- ZStream(
                       sizedArchiveEntry(file1Name, file1Content),
                       sizedArchiveEntry(file2Name, file2Content),
                     )
                       .via(ZipArchiver.archive)
                       .via(ZipUnarchiver.list)
                       .runCollect
        } yield {
          val zipMethods = entries.map(_.underlying[ZipEntry].getMethod)
          assertTrue(
            entries.size == 2,
            zipMethods.forall(_ == ZipEntry.DEFLATED),
          )
        }
      },
      test("stored Zip file contains stored entries") {
        for {
          entries <- ZStream(
                       sizedArchiveEntry(file1Name, file1Content),
                       sizedArchiveEntry(file2Name, file2Content),
                     )
                       .via(ZipArchiver(zipMethod = ZipMethod.Stored).archive)
                       .via(ZipUnarchiver.list)
                       .runCollect
        } yield {
          val zipMethods = entries.map(_.underlying[ZipEntry].getMethod)
          assertTrue(
            entries.size == 2,
            zipMethods.forall(_ == ZipEntry.STORED),
          )
        }
      },
      test("system unzip can process Zip file") {
        for {
          tmpDir <- TempDir.temporaryDirectory
          zipFile = tmpDir.resolve("test.zip")
          _ <- ZStream(
                 archiveEntry(file1Name, file1Content),
                 archiveEntry(file2Name, file2Content),
               )
                 .via(ZipArchiver.archive)
                 .run(ZSink.fromPath(zipFile))
          extractDir <- TempDir.temporaryDirectory
          exitCode <- Command("unzip", "-q", zipFile.toString, "-d", extractDir.toString).exitCode
          extracted1 <- ZStream.fromPath(extractDir.resolve(file1Name)).via(ZPipeline.utf8Decode).runHead
          extracted2 <- ZStream.fromPath(extractDir.resolve(file2Name)).via(ZPipeline.utf8Decode).runHead
        } yield
          assertTrue(
            exitCode.code == 0,
            extracted1.contains(file1Content),
            extracted2.contains(file2Content),
          )
      },
      test("zip archiver drains directory entry content") {
        // ZipArchiver must drain the content stream of a directory entry even though directories have no content.
        // Without draining, the pipeline can stall because the content streams from ZipUnarchiver are sequentially
        // gated: the next entry is not emitted until the previous entry's content stream has been fully consumed.
        // So if ZipArchiver skips draining the directory's content stream, the file entry after it is never emitted.
        //
        // A zip with a directory entry first, then a file:
        //   mkdir subdir && zip - subdir/ file.txt
        val zipWithDirectory = Chunk.fromArray(
          Base64.getDecoder.decode(
            "UEsDBAoAAAAAADijc10AAAAAAAAAAAAAAAAHABwAc3ViZGlyL1VUCQADO03/ajtN/2p1eAsAAQT1AQAA" +
              "BAAAAABQSwMEFAAIAAgAOKNzXQAAAAAAAAAABgAAAAgAHABmaWxlLnR4dFVUCQADO03/ajtN/2p1eAsA" +
              "AQT1AQAABAAAAADzSM3JyVcEAFBLBwhWzCqdCAAAAAYAAABQSwECHgMKAAAAAAA4o3NdAAAAAAAAAAAA" +
              "AAAABwAYAAAAAAAAABAA7UEAAAAAc3ViZGlyL1VUBQADO03/anV4CwABBPUBAAAEAAAAAFBLAQIeAxQA" +
              "CAAIAOKY811WzCqdCAAAAAYAAAAIABgAAAAAAAEAAACkgUEAAABmaWxlLnR4dFVUBQADO03/anV4CwAB" +
              "BPUBAAAEAAAAAFBLBQYAAAAAAgACAJsAAACbAAAAAAA="
          )
        )
        for {
          entries <-
            ZStream
              .fromChunk(zipWithDirectory)
              // Unarchive: produces sequentially-gated (directory, content), (file, content) pairs.
              // ZipUnarchiver won't emit the second pair until the first content stream is fully consumed.
              .via(ZipUnarchiver.unarchive)
              // Re-archive: ZipArchiver MUST drain each content stream, including the empty directory one.
              .via(ZipArchiver.archive)
              .via(ZipUnarchiver.unarchive)
              .mapZIO { case (entry, stream) => stream.runDrain.as(entry.name) }
              .runCollect
        } yield
          assertTrue(
            entries.contains("subdir/"),
            entries.contains("file.txt"),
          )
      } @@ TestAspect.withLiveClock @@ TestAspect.timeout(5.seconds),
      test("system unzip can process stored Zip file") {
        for {
          tmpDir <- TempDir.temporaryDirectory
          zipFile = tmpDir.resolve("test.zip")
          _ <- ZStream(
                 sizedArchiveEntry(file1Name, file1Content),
                 sizedArchiveEntry(file2Name, file2Content),
               )
                 .via(ZipArchiver(zipMethod = ZipMethod.Stored).archive)
                 .run(ZSink.fromPath(zipFile))
          extractDir <- TempDir.temporaryDirectory
          exitCode <- Command("unzip", "-q", zipFile.toString, "-d", extractDir.toString).exitCode
          extracted1 <- ZStream.fromPath(extractDir.resolve(file1Name)).via(ZPipeline.utf8Decode).runHead
          extracted2 <- ZStream.fromPath(extractDir.resolve(file2Name)).via(ZPipeline.utf8Decode).runHead
        } yield
          assertTrue(
            exitCode.code == 0,
            extracted1.contains(file1Content),
            extracted2.contains(file2Content),
          )
      },
    )

  private def archiveEntry(
    name: String,
    content: String,
  ): (ArchiveEntry[Option, Any], ZStream[Any, Throwable, Byte]) = {
    val contentBytes = content.getBytes(UTF_8)
    (ArchiveEntry(name), ZStream.fromIterable(contentBytes))
  }

  private def sizedArchiveEntry(
    name: String,
    content: String,
  ): (ArchiveEntry[Some, Any], ZStream[Any, Throwable, Byte]) = {
    val contentBytes = content.getBytes(UTF_8)
    (ArchiveEntry(name, Some(contentBytes.size.toLong)), ZStream.fromIterable(contentBytes))
  }

  // used to prevent compile warnings like:
  // [error] too many arguments (2) for method ==: (x$1: Any)Boolean
  private def t(s1: String, s2: String): (String, String) = (s1, s2)
}
