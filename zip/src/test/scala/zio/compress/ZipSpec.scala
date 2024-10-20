package zio.compress

import zio._
import zio.compress.Zip.zipArchiveEntryToUnderlying
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
  private val zipArchive = Chunk.fromArray(
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

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("Zip")(
      test("zip unarchive") {
        for {
          obtained <- ZStream
                        .fromChunk(zipArchive)
                        .via(ZipUnarchiver.make().unarchive)
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
        checkN(10)(Gen.int(40, 5000), Gen.chunkOfBounded(0, 20000)(Gen.byte)) { (chunkSize, genBytes) =>
          for {
            obtained <-
              ZStream
                .fromChunk(genBytes)
                .rechunk(chunkSize)
                .via(ArchiveSingleFileCompressor.forName(ZipArchiver.make(), "test", genBytes.length.toLong).compress)
                .via(ArchiveSingleFileDecompressor(ZipUnarchiver.make()).decompress)
                .runCollect
          } yield assertTrue(obtained == genBytes)
        }
      },
      test("zip compress without entry size") {
        for {
          obtained <-
            ZStream(archiveEntry("readme.txt", "Hello world!"))
              .via(ZipArchiver.make().archive)
              .via(ZipUnarchiver.make().unarchive)
              .runCollect
        } yield
          assertTrue(obtained.head._1.name == "readme.txt")
      },
      test("copy zip entry") {
        val entry = ArchiveEntry(name = "test")
        val zipEntry = entry.withUnderlying(entry.underlying[ZipEntry])
        val zipEntry2 = zipEntry.withName("test2")
        val zipArchiveEntry = zipEntry2.underlying[ZipEntry] // underlying ZipArchiveEntry entry is copied
        assertTrue(zipArchiveEntry.getName == "test2")
      },
    )

  private def archiveEntry(
    name: String,
    content: String,
  ): (ArchiveEntry[Option, Any], ZStream[Any, Throwable, Byte]) = {
    val contentBytes = content.getBytes(UTF_8)
    (ArchiveEntry(name), ZStream.fromIterable(contentBytes))
  }

  // used to prevent compile warnings like:
  // [error] too many arguments (2) for method ==: (x$1: Any)Boolean
  private def t(s1: String, s2: String): (String, String) = (s1, s2)
}
