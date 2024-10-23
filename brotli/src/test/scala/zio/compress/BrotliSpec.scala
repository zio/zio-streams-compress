package zio.compress

import zio._
import zio.test._
import zio.stream._

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64

object BrotliSpec extends ZIOSpecDefault {

  private final val clear = Chunk.fromArray("hello world!".getBytes(UTF_8))
  private final val compressed = Chunk.fromArray(Base64.getDecoder.decode("iwWAaGVsbG8gd29ybGQhAw=="))

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("Brotli")(
      test("brotli decompress") {
        for {
          obtained <- ZStream
                        .fromChunk(compressed)
                        .via(BrotliDecompressor.decompress)
                        .runCollect
        } yield assertTrue(clear == obtained)
      }
    )

}
