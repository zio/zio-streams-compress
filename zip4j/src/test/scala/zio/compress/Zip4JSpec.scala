package zio.compress

import zio._
import zio.stream._
import zio.test._

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64

object Zip4JSpec extends ZIOSpecDefault {
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

  /* Created as above, with password "secret" (using the `-e` option)  */
  private val encryptedZipArchive = Chunk.fromArray(
    Base64
      .getDecoder
      .decode(
        "UEsDBBQACQAIACB9SlkAACB9AAAAAAwAAAAJABwAZmlsZTEudHh0VVQJAANr2Qdna9kHZ3V4CwAB" +
          "BOgDAAAE6AMAAKX+yuoRx1uZ+JfBFduDbdvt4DTyA9cqw97vUEsHCJUZhRsaAAAADAAAAFBLAwQU" +
          "AAkACAAgfUpZAAAgfQAAAAASAAAAEAAcAHN1YmRpci9maWxlMi50eHRVVAkAA2vZB2dr2QdndXgL" +
          "AAEE6AMAAAToAwAA4KYKUQzabVa51KZI/KUfUqTQz6Ulq2bWx/xEQ7tF6hlQSwcIe/g7byAAAAAS" +
          "AAAAUEsBAh4DFAAJAAgAIH1KWZUZhRsaAAAADAAAAAkAGAAAAAAAAQAAALSBAAAAAGZpbGUxLnR4" +
          "dFVUBQADa9kHZ3V4CwABBOgDAAAE6AMAAFBLAQIeAxQACQAIACB9Sll7+DtvIAAAABIAAAAQABgA" +
          "AAAAAAEAAAC0gW0AAABzdWJkaXIvZmlsZTIudHh0VVQFAANr2QdndXgLAAEE6AMAAAToAwAAUEsF" +
          "BgAAAAACAAIApQAAAOcAAAAAAA=="
      )
  )

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("Zip4J")(
      test("zip4j unarchive") {
        for {
          obtained <- ZStream
                        .fromChunk(zipArchive)
                        .via(Zip4JUnarchiver.make().unarchive)
                        .mapZIO { case (archiveEntry, stream) =>
                          for {
                            content <- stream.runCollect
                          } yield archiveEntry.name -> new String(content.toArray)
                        }
                        .runCollect
        } yield
          assertTrue(
            obtained.head == ("file1.txt", "Hello world!"),
            obtained(1) == ("subdir/file2.txt", "Hello from subdir!"),
          )
      },
      test("zip4j unarchive with password") {
        for {
          obtained <- ZStream
                        .fromChunk(encryptedZipArchive)
                        .via(Zip4JUnarchiver.make(password = Some("secret")).unarchive)
                        .mapZIO { case (archiveEntry, stream) =>
                          for {
                            content <- stream.runCollect
                          } yield archiveEntry.name -> new String(content.toArray)
                        }
                        .runCollect
        } yield
          assertTrue(
            obtained.head == ("file1.txt", "Hello world!"),
            obtained(1) == ("subdir/file2.txt", "Hello from subdir!"),
          )
      },
      test("zip4j round trip") {
        checkN(10)(Gen.int(40, 5000), Gen.chunkOfBounded(0, 20000)(Gen.byte)) { (chunkSize, genBytes) =>
          for {
            obtained <-
              ZStream
                .fromChunk(genBytes)
                .rechunk(chunkSize)
                .via(ArchiveSingleFileCompressor.forName(Zip4JArchiver.make(), "test", genBytes.length.toLong).compress)
                .via(ArchiveSingleFileDecompressor(Zip4JUnarchiver.make()).decompress)
                .runCollect
          } yield assertTrue(obtained == genBytes)
        }
      },
      test("zip4j archive wrong size") {
        for {
          obtained <- ZStream(
                        archiveEntry("file1.txt", 12, "Hello world!"),
                        archiveEntry("subdir/file2.txt", 999999, "Hello from subdir!"),
                      )
                        .via(Zip4JArchiver.make().archive)
                        .runCollect
                        .exit
        } yield
          assertTrue(
            obtained.is(_.die).getMessage ==
              "Entry size of 18 bytes does not match size of 999999 bytes specified in entry"
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
