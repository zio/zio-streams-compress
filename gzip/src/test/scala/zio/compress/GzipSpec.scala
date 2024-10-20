package zio.compress

import zio._
import zio.stream._
import zio.test._

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64

object GzipSpec extends ZIOSpecDefault {
  private val clear = Chunk.fromArray("Hello world!".getBytes(UTF_8))
  private val compressed = Chunk.fromArray(
    Base64.getDecoder.decode("H4sIAMKVBmcAA/NIzcnJVyjPL8pJUQQAlRmFGwwAAAA=")
  )

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("Gzip")(
      test("gzip decompress") {
        for {
          obtained <- ZStream
                        .fromChunk(compressed)
                        .via(GzipDecompressor.make().decompress)
                        .runCollect
        } yield assertTrue(clear == obtained)
      },
      test("gzip round trip") {
        checkN(10)(Gen.int(40, 5000), Gen.chunkOfBounded(0, 20000)(Gen.byte)) { (chunkSize, genBytes) =>
          for {
            obtained <- ZStream
                          .fromChunk(genBytes)
                          .rechunk(chunkSize)
                          .via(GzipCompressor.make().compress)
                          .via(GzipDecompressor.make().decompress)
                          .runCollect
          } yield assertTrue(obtained == genBytes)
        }
      },
    )

}
