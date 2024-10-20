package zio.compress

import zio._
import zio.stream._
import zio.test._

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64

object Bzip2Spec extends ZIOSpecDefault {

  private val clear = Chunk.fromArray("Hello world!".getBytes(UTF_8))
  private val compressed = Chunk.fromArray(
    Base64.getDecoder.decode("QlpoOTFBWSZTWQNY9XcAAAEVgGAAAEAGBJCAIAAxBkxBA0wi4Itio54u5IpwoSAGseru")
  )

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("Bzip2")(
      test("bzip2 compress") {
        for {
          obtained <- ZStream
                        .fromChunk(clear)
                        .via(Bzip2Compressor.make().compress)
                        .runCollect
        } yield assertTrue(compressed == obtained)
      },
      test("bzip2 decompress") {
        for {
          obtained <- ZStream
                        .fromChunk(compressed)
                        .via(Bzip2Decompressor.make().decompress)
                        .runCollect
        } yield assertTrue(clear == obtained)
      },
      test("bzip2 round trip") {
        checkN(10)(Gen.int(40, 5000), Gen.chunkOfBounded(0, 20000)(Gen.byte)) { (chunkSize, genBytes) =>
          for {
            obtained <- ZStream
                          .fromChunk(genBytes)
                          .rechunk(chunkSize)
                          .via(Bzip2Compressor.make().compress)
                          .via(Bzip2Decompressor.make().decompress)
                          .runCollect
          } yield assertTrue(obtained == genBytes)
        }
      },
    )
}
