package zio.compress

import zio._
import zio.stream._
import zio.test._

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64

object Brotli4JSpec extends ZIOSpecDefault {
  private final val clear = Chunk.fromArray("hello world!".getBytes(UTF_8))
  private final val compressed = Chunk.fromArray(Base64.getDecoder.decode("iwWAaGVsbG8gd29ybGQhAw=="))

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("Brotli4J")(
      test("brotli4J decompress") {
        for {
          obtained <- ZStream
                        .fromChunk(compressed)
                        .via(Brotli4JDecompressor.make().decompress)
                        .runCollect
        } yield assertTrue(clear == obtained)
      },
      test("brotli4J round trip") {
        checkN(10)(Gen.int(40, 5000), Gen.chunkOfBounded(0, 20000)(Gen.byte)) { (chunkSize, genBytes) =>
          for {
            obtained <- ZStream
                          .fromChunk(genBytes)
                          .rechunk(chunkSize)
                          .via(Brotli4JCompressor.make().compress)
                          .via(Brotli4JDecompressor.make().decompress)
                          .runCollect
          } yield assertTrue(obtained == genBytes)
        }
      },
    )
}
