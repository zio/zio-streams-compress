package zio.compress

import zio._
import zio.test._
import zio.stream._

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64

object Lz4Spec extends ZIOSpecDefault {
  private val clear = Chunk.fromArray("Hello world!".getBytes(UTF_8))
  private val compressed = Chunk.fromArray(
    Base64.getDecoder.decode("BCJNGGRApwwAAIBIZWxsbyB3b3JsZCEAAAAAcXerxA==")
  )

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("Lz4")(
      test("lz4 decompress") {
        for {
          obtained <- ZStream
                        .fromChunk(compressed)
                        .via(Lz4Decompressor.make().decompress)
                        .runCollect
        } yield assertTrue(clear == obtained)
      },
      test("lz4 round trip") {
        checkN(10)(Gen.int(40, 5000), Gen.chunkOfBounded(0, 20000)(Gen.byte)) { (chunkSize, genBytes) =>
          for {
            obtained <- ZStream
                          .fromChunk(genBytes)
                          .rechunk(chunkSize)
                          .via(Lz4Compressor.make().compress)
                          .via(Lz4Decompressor.make().decompress)
                          .runCollect
          } yield assertTrue(obtained == genBytes)
        }
      },
    )
}
