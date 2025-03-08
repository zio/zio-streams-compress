package zio.compress

import zio._
import zio.test._
import zio.stream._

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64

object SnappySpec extends ZIOSpecDefault {
  private final val clear = Chunk.fromArray("Hello world!".getBytes(UTF_8))
  // brew install snzip
  // echo -n 'Hello world!' | snzip -t framing2 | base64
  private final val compressedFramed2 = Chunk.fromArray(
    Base64.getDecoder.decode("/wYAAHNOYVBwWQEQAAAJ4iVxSGVsbG8gd29ybGQh")
  )
  // brew install snzip
  // echo -n 'Hello world!' | snzip -t snappy-java | base64
  private final val compressedBasic = Chunk.fromArray(
    Base64.getDecoder.decode("glNOQVBQWQAAAAABAAAAAQAAAA4MLEhlbGxvIHdvcmxkIQ==")
  )
  // brew install snzip
  // echo -n 'Hello world!' | snzip -t hadoop-snappy | base64
  private final val compressedHadoop = Chunk.fromArray(
    Base64.getDecoder.decode("AAAADAAAAA4MLEhlbGxvIHdvcmxkIQ==")
  )

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("Snappy")(
      test("Snappy decompress framed") {
        for {
          obtained <- ZStream
                        .fromChunk(compressedFramed2)
                        .via(SnappyDecompressor.decompress)
                        .runCollect
        } yield assertTrue(clear == obtained)
      },
      test("Snappy decompress basic") {
        for {
          obtained <- ZStream
                        .fromChunk(compressedBasic)
                        .via(SnappyDecompressor(SnappyReadFormat.Basic()).decompress)
                        .runCollect
        } yield assertTrue(clear == obtained)
      },
      test("Snappy compress hadoop") {
        for {
          obtained <- ZStream
                        .fromChunk(clear)
                        .via(SnappyCompressor(SnappyWriteFormat.HadoopCompatible()).compress)
                        .runCollect
        } yield assertTrue(obtained == compressedHadoop)
      },
      test("Snappy round trip framed") {
        checkN(10)(Gen.int(40, 5000), Gen.chunkOfBounded(0, 20000)(Gen.byte)) { (chunkSize, genBytes) =>
          for {
            obtained <- ZStream
                          .fromChunk(genBytes)
                          .rechunk(chunkSize)
                          .via(SnappyCompressor.compress)
                          .via(SnappyDecompressor.decompress)
                          .runCollect
          } yield assertTrue(obtained == genBytes)
        }
      },
      test("Snappy round trip basic") {
        checkN(10)(Gen.int(40, 5000), Gen.chunkOfBounded(0, 20000)(Gen.byte)) { (chunkSize, genBytes) =>
          for {
            obtained <- ZStream
                          .fromChunk(genBytes)
                          .rechunk(chunkSize)
                          .via(SnappyCompressor(SnappyWriteFormat.Basic()).compress)
                          .via(SnappyDecompressor(SnappyReadFormat.Basic()).decompress)
                          .runCollect
          } yield assertTrue(obtained == genBytes)
        }
      },
    )
}
