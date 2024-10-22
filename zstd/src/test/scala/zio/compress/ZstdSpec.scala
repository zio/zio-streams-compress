package zio.compress

import zio._
import zio.stream._
import zio.test._

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64

object ZstdSpec extends ZIOSpecDefault {
  private final val clear = Chunk.fromArray("Hello world!".getBytes(UTF_8))
  private final val compressed = Chunk.fromArray(
    Base64.getDecoder.decode("KLUv/QRYYQAASGVsbG8gd29ybGQhsn39fw==")
  )

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("Zstd")(
      test("zstd decompress") {
        for {
          obtained <- ZStream
                        .fromChunk(compressed)
                        .via(ZstdDecompressor.make().decompress)
                        .runCollect
        } yield assertTrue(clear == obtained)
      },
      test("zstd round trip") {
        checkN(10)(Gen.int(40, 5000), Gen.chunkOfBounded(0, 20000)(Gen.byte)) { (chunkSize, genBytes) =>
          for {
            obtained <- ZStream
                          .fromChunk(genBytes)
                          .rechunk(chunkSize)
                          .via(ZstdCompressor.make().compress)
                          .via(ZstdDecompressor.make().decompress)
                          .runCollect
          } yield assertTrue(obtained == genBytes)
        }
      },
    )
}
