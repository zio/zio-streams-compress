package zio.compress

import net.lingala.zip4j.model.LocalFileHeader
import net.lingala.zip4j.model.enums.{CompressionMethod, EncryptionMethod => Zip4JEncryptionMethod}
import zio._
import zio.compress.zip4j.EncryptionMethod
import zio.compress.Zip4J.zip4jUnarchiveEntryToUnderlying
import zio.compress.zip4j.ZipMethod
import zio.process._
import zio.stream._
import zio.test._

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
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
  private val password = "secret"

  /* Created as above, with password "secret" (using the `-e` option)  */
  private final val encryptedZipArchive = Chunk.fromArray(
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
                        .via(Zip4JUnarchiver.unarchive)
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
      test("zip4j unarchive with password") {
        for {
          obtained <- ZStream
                        .fromChunk(encryptedZipArchive)
                        .via(Zip4JUnarchiver(password = Some("secret")).unarchive)
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
      test("zip4j round trip") {
        checkN(400)(
          Gen.int(40, 5000),
          Gen.chunkOfBounded(0, 20000)(Gen.byte),
          Gen.option(Gen.fromIterable(zip4j.EncryptionMethod.Values)),
          Gen.fromIterable(zip4j.ZipMethod.Values),
        ) { (chunkSize, genBytes, encryptionMethod, zipMethod) =>
          val archiver = encryptionMethod match {
            case None     => Zip4JArchiver(zipMethod = zipMethod)
            case Some(em) => Zip4JArchiver.encrypted(password = "secret", zipMethod = zipMethod, encryptionMethod = em)
          }
          for {
            obtained <-
              ZStream
                .fromChunk(genBytes)
                .rechunk(chunkSize)
                .via(ArchiveSingleFileCompressor.forName(archiver, "test", genBytes.length.toLong).compress)
                .via(ArchiveSingleFileDecompressor(Zip4JUnarchiver(password = Some("secret"))).decompress)
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
                        .via(Zip4JArchiver.archive)
                        .runCollect
                        .exit
        } yield
          assertTrue(
            obtained.is(_.die).getMessage ==
              "Entry size of 18 bytes does not match size of 999999 bytes specified in entry"
          )
      },
      test("deflated Zip4J file contains deflated entries") {
        for {
          entries <- ZStream(
                       archiveEntry(file1Name, file1Content.getBytes(UTF_8).length.toLong, file1Content),
                       archiveEntry(file2Name, file2Content.getBytes(UTF_8).length.toLong, file2Content),
                     )
                       .via(Zip4JArchiver(zipMethod = ZipMethod.Deflated).archive)
                       .via(Zip4JUnarchiver.list)
                       .runCollect
        } yield {
          val zipMethods = entries.map(_.underlying[LocalFileHeader].getCompressionMethod)
          assertTrue(
            entries.size == 2,
            zipMethods.forall(_ == CompressionMethod.DEFLATE),
          )
        }
      },
      test("stored Zip4J file contains stored entries") {
        for {
          entries <- ZStream(
                       archiveEntry(file1Name, file1Content.getBytes(UTF_8).length.toLong, file1Content),
                       archiveEntry(file2Name, file2Content.getBytes(UTF_8).length.toLong, file2Content),
                     )
                       .via(Zip4JArchiver(zipMethod = ZipMethod.Stored).archive)
                       .via(Zip4JUnarchiver.list)
                       .runCollect
        } yield {
          val zipMethods = entries.map(_.underlying[LocalFileHeader].getCompressionMethod)
          assertTrue(
            entries.size == 2,
            zipMethods.forall(_ == CompressionMethod.STORE),
          )
        }
      },
      test("encrypted Zip4J file contains encrypted entries") {
        for {
          entries <- ZStream(
                       archiveEntry(file1Name, file1Content.getBytes(UTF_8).length.toLong, file1Content),
                       archiveEntry(file2Name, file2Content.getBytes(UTF_8).length.toLong, file2Content),
                     )
                       .via(Zip4JArchiver.encrypted(password, EncryptionMethod.ZipStandard).archive)
                       .via(Zip4JUnarchiver(password = Some(password)).list)
                       .runCollect
        } yield {
          val encryptionMethods = entries.map(_.underlying[LocalFileHeader].getEncryptionMethod)
          assertTrue(
            entries.size == 2,
            encryptionMethods.forall(_ == Zip4JEncryptionMethod.ZIP_STANDARD),
          )
        }
      },
      test("system unzip can process Zip4J file") {
        systemZipTest(
          Zip4JArchiver(),
          (zipFile: Path, extractDir: Path) =>
            Command("unzip", "-q", zipFile.toString, "-d", extractDir.toString),
        )
      },
      test("system unzip can process encrypted Zip4J file") {
        systemZipTest(
          Zip4JArchiver.encrypted(password, EncryptionMethod.ZipStandard),
          (zipFile: Path, extractDir: Path) =>
            Command("unzip", "-P", password, "-q", zipFile.toString, "-d", extractDir.toString),
        )
      },
      test("system unzip can process stored Zip4J file") {
        systemZipTest(
          Zip4JArchiver(zipMethod = ZipMethod.Stored),
          (zipFile: Path, extractDir: Path) =>
            Command("unzip", "-q", zipFile.toString, "-d", extractDir.toString),
        )
      },
      test("system unzip can process stored and encrypted Zip4J file") {
        systemZipTest(
          Zip4JArchiver.encrypted(password, EncryptionMethod.ZipStandard, ZipMethod.Stored),
          (zipFile: Path, extractDir: Path) =>
            Command("unzip", "-P", password, "-q", zipFile.toString, "-d", extractDir.toString),
        )
      },
    )

  private def systemZipTest(
    archiver: Zip4JArchiver,
    systemUnzipCommand: (Path, Path) => Command.Standard,
  ): ZIO[Scope, Throwable, TestResult] =
    for {
      tmpDir <- TempDir.temporaryDirectory
      zipFile = tmpDir.resolve("test.zip")
      _ <- ZStream(
             archiveEntry(file1Name, file1Content.getBytes(UTF_8).length.toLong, file1Content),
             archiveEntry(file2Name, file2Content.getBytes(UTF_8).length.toLong, file2Content),
           )
             .via(archiver.archive)
             .run(ZSink.fromPath(zipFile))
      extractDir <- TempDir.temporaryDirectory
      exitCode <- systemUnzipCommand(zipFile, extractDir).exitCode
      extracted1 <- ZStream.fromPath(extractDir.resolve(file1Name)).via(ZPipeline.utf8Decode).runHead
      extracted2 <- ZStream.fromPath(extractDir.resolve(file2Name)).via(ZPipeline.utf8Decode).runHead
    } yield
      assertTrue(
        exitCode.code == 0,
        extracted1.contains(file1Content),
        extracted2.contains(file2Content),
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
