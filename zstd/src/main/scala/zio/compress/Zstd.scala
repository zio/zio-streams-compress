package zio.compress

import com.github.luben.zstd.{ZstdInputStream, ZstdOutputStream}
import zio.Trace
import zio.compress.JavaIoInterop.{viaInputStreamByte, viaOutputStreamByte}
import zio.stream._

import java.io.BufferedInputStream

/** Creates [[ZstdCompressor]]s: `apply` takes the options, and `compress` gives the pipeline with the default options.
  */
object ZstdCompressor {

  /** A [[Compressor]] for Zstd, based on the com.github.luben:zstd-jni library.
    *
    * @param level
    *   The compression level to use, or `None` for the zstd-jni default, which is level 3. Defaults to `None`.
    * @param workers
    *   The number of worker threads that compress in parallel, or `None` for no worker threads. `Some(0)` also starts
    *   no worker threads, and a negative value gives the same output as `Some(0)`. The native library caps the number
    *   of worker threads. Defaults to `None`.
    * @param customDictionary
    *   a custom dictionary, or `None` for no custom dictionary. [[ZstdDecompressor]] has no dictionary option.
    */
  def apply(
    level: Option[ZstdCompressionLevel] = None,
    workers: Option[Int] = None,
    customDictionary: Option[Array[Byte]] = None,
  ): ZstdCompressor =
    new ZstdCompressor(level, workers, customDictionary)

  /** Returns the compression pipeline of a [[ZstdCompressor]] with the default options, the same as
    * `ZstdCompressor().compress`.
    */
  def compress: ZPipeline[Any, Throwable, Byte, Byte] = apply().compress
}

/** A [[Compressor]] that writes the Zstandard (zstd) format, through the native library of `com.github.luben:zstd-jni`.
  * Use it to produce Zstandard data, such as the content of a `.zst` file. Create one with `ZstdCompressor(...)` to
  * choose the compression level, the number of worker threads or a dictionary, or use `ZstdCompressor.compress` for the
  * defaults. Read the result back with [[ZstdDecompressor]].
  *
  * {{{
  * import zio._
  * import zio.compress._
  * import zio.stream._
  *
  * val chapters: Chunk[Byte] = Chunk.fromArray(("Chapter 1: Pattern matching in Scala\n" * 200).getBytes("UTF-8"))
  *
  * // compressedSize succeeds with 55 (bytes, from 7400 input bytes)
  * val compressedSize: Task[Long] =
  *   ZStream
  *     .fromChunk(chapters)
  *     .via(ZstdCompressor(level = ZstdCompressionLevel(19), workers = Some(2)).compress)
  *     .runCount
  * }}}
  *
  * @see
  *   [[https://zio.dev/zio-streams-compress/reference-zstd/ Zstd]]
  */
final class ZstdCompressor private (
  level: Option[ZstdCompressionLevel],
  workers: Option[Int],
  customDictionary: Option[Array[Byte]],
) extends Compressor {

  override def compress(implicit trace: Trace): ZPipeline[Any, Throwable, Byte, Byte] =
    viaOutputStreamByte { outputStream =>
      val zstdOutputStream = new ZstdOutputStream(outputStream)
      level.foreach(l => zstdOutputStream.setLevel(l.level))
      workers.foreach(zstdOutputStream.setWorkers)
      customDictionary.foreach(zstdOutputStream.setDict)
      zstdOutputStream
    }
}

/** Creates [[ZstdDecompressor]]s: `apply` takes the options, and `decompress` gives the pipeline with the default
  * options.
  */
object ZstdDecompressor {

  /** A [[Decompressor]] for Zstd, based on the com.github.luben:zstd-jni library.
    *
    * @param chunkSize
    *   The maximum chunk size of the outgoing ZStream. Defaults to `ZStream.DefaultChunkSize` (4KiB).
    */
  def apply(chunkSize: Int = ZStream.DefaultChunkSize): ZstdDecompressor =
    new ZstdDecompressor(chunkSize)

  /** Returns the decompression pipeline of a [[ZstdDecompressor]] with the default chunk size, the same as
    * `ZstdDecompressor().decompress`.
    */
  def decompress: ZPipeline[Any, Throwable, Byte, Byte] = apply().decompress
}

/** A [[Decompressor]] that reads the Zstandard (zstd) format, through the native library of
  * `com.github.luben:zstd-jni`. Use it to read Zstandard data, such as the content of a `.zst` file or the output of
  * [[ZstdCompressor]]. Create one with `ZstdDecompressor(chunkSize)` to set the largest chunk it emits, or use
  * `ZstdDecompressor.decompress` for the default of 4 KiB. It has no dictionary option.
  *
  * Input that does not start with a Zstandard frame fails the stream with
  * `com.github.luben.zstd.ZstdIOException: Unknown frame descriptor`, and input that is cut short fails with
  * `ZstdIOException: Truncated source`. Empty input gives empty output.
  *
  * {{{
  * import zio._
  * import zio.compress._
  * import zio.stream._
  *
  * val title: String = "Programming in Scala, 5th edition"
  *
  * // roundTrip succeeds with "Programming in Scala, 5th edition"
  * val roundTrip: Task[String] =
  *   ZStream
  *     .fromIterable(title.getBytes("UTF-8"))
  *     .via(ZstdCompressor.compress)
  *     .via(ZstdDecompressor(chunkSize = 16 * 1024).decompress)
  *     .runCollect
  *     .map(bytes => new String(bytes.toArray, "UTF-8"))
  * }}}
  *
  * @see
  *   [[https://zio.dev/zio-streams-compress/reference-zstd/ Zstd]]
  */
final class ZstdDecompressor private (chunkSize: Int) extends Decompressor {

  override def decompress(implicit trace: Trace): ZPipeline[Any, Throwable, Byte, Byte] =
    // ZstdInputStream.read does not try to read the requested number of bytes, but it does have a good
    // `available()` implementation, so with buffering we can still get full chunks.
    viaInputStreamByte(chunkSize) { inputStream =>
      new BufferedInputStream(new ZstdInputStream(inputStream), chunkSize)
    }
}
