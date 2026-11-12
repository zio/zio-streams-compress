package zio.compress

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import zio._
import zio.compress.Tar.tarArchiveEntryToUnderlying
import zio.process.Command
import zio.test._
import zio.stream._

import java.util.Base64
import java.nio.charset.StandardCharsets.UTF_8

object TarSpec extends ZIOSpecDefault {
  /* Created on an Ubuntu system with:
   * {{{
   *   mkdir tgztempdir
   *   cd tgztempdir
   *   echo -n 'Hello world!' > file1.txt
   *   mkdir -p subdir
   *   echo -n 'Hello from subdir!' > subdir/file2.txt
   *   tar -czO file1.txt subdir/file2.txt | base64; echo
   *   cd ..
   *   rm -rf tgztempdir
   * }}}
   */
  private final val tgzArchive = Chunk.fromArray(
    Base64
      .getDecoder
      .decode(
        "H4sIAAAAAAAAA+3UQQqDMBCF4RwlvUCbicZcoddo0YA0RYhKe/wqgVK6qCstwv9tBpIsZpH3Qhsb" +
          "OQ7PQa3HTKqqnKd4Zz5nJqWS0k+HhbeFKCNiK6+0WXGnt7EfLklr1aT29uvd0v1OnZsYO/3oUqwP" +
          "/94F2+vHa92mU5hqwK5VA4v5t/Yr/9Y58r+JnP+QurvOX4EWAAAAAAAAAAAAAAAA2JUX9n5LGQAo" +
          "AAA="
      )
  )

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("Tar")(
      test("tar unarchive") {
        for {
          obtained <- ZStream
                        .fromChunk(tgzArchive)
                        .via(GzipDecompressor.decompress)
                        .via(TarUnarchiver.unarchive)
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
      test("tar round trip") {
        checkN(10)(Gen.int(40, 5000), Gen.chunkOfBounded(0, 20000)(Gen.byte)) { (chunkSize, genBytes) =>
          for {
            obtained <-
              ZStream
                .fromChunk(genBytes)
                .rechunk(chunkSize)
                .via(ArchiveSingleFileCompressor.forName(TarArchiver(), "test", genBytes.length.toLong).compress)
                .via(ArchiveSingleFileDecompressor(TarUnarchiver()).decompress)
                .runCollect
          } yield assertTrue(obtained == genBytes)
        }
      },
      test("tar archive wrong size") {
        for {
          obtained <- ZStream(
                        archiveEntry("file1.txt", 12, "Hello world!"),
                        archiveEntry("subdir/file2.txt", 999999, "Hello from subdir!"),
                      )
                        .via(TarArchiver.archive)
                        .runCollect
                        .exit
        } yield
          assertTrue(
            obtained.is(_.die).getMessage ==
              "Entry size of 18 bytes does not match size of 999999 bytes specified in entry"
          )
      },
      test("copy tar entry") {
        val entry = ArchiveEntry(name = "test")
        val tarEntry = entry.withUnderlying(entry.underlying[TarArchiveEntry])
        val tarEntry2 = tarEntry.withName("test2")
        val tarArchiveEntry = tarEntry2.underlying[TarArchiveEntry] // underlying TarArchiveEntry entry is copied
        assertTrue(tarArchiveEntry.getName == "test2")
      },
      test("system tar can untar Tar file") {
        val file1Name = "file1.txt"
        val file1Content = "Hello world!"
        val file2Name = "subdir/file2.txt"
        val file2Content = "Hello from subdir!"

        for {
          tmpDir <- TempDir.temporaryDirectory
          tarFile = tmpDir.resolve("test.tar")
          _ <- ZStream(
                 archiveEntry(file1Name, file1Content.getBytes(UTF_8).length.toLong, file1Content),
                 archiveEntry(file2Name, file2Content.getBytes(UTF_8).length.toLong, file2Content),
               )
                 .via(TarArchiver.archive)
                 .run(ZSink.fromPath(tarFile))
          extractDir <- TempDir.temporaryDirectory
          exitCode <- Command("tar", "-x", "-f", tarFile.toString, "-C", extractDir.toString).exitCode
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
    size: Long,
    content: String,
  ): (ArchiveEntry[Some, Any], ZStream[Any, Throwable, Byte]) = {
    val contentBytes = content.getBytes(UTF_8)
    (ArchiveEntry(name, Some(size)), ZStream.fromIterable(contentBytes))
  }

  // used to prevent compile warnings like:
  // [error] too many arguments (2) for method ==: (x$1: Any)Boolean
  private def t(s1: String, s2: String): (String, String) = (s1, s2)
}
