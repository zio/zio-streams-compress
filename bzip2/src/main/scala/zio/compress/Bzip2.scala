package zio.compress

import org.apache.commons.compress.compressors.bzip2.{BZip2CompressorInputStream, BZip2CompressorOutputStream}
import zio.compress.JavaIoInterop.{viaInputStreamByte, viaOutputStreamByte}
import zio.stream._
import zio.Trace

/** Creates [[Bzip2Compressor]]s: `apply` takes the options, and `compress` gives the pipeline with the default options.
  */
object Bzip2Compressor {

  /** A [[Compressor]] for Bzip2, based on the Apache Commons Compress library.
    *
    * Note: Bzip2 uses a lot of memory. See
    * [[org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream]] for an overview of the required heap
    * size for each block size.
    *
    * @param blockSize
    *   the block size to use, or `None` for 900KB (`Bzip2BlockSize.BlockSize900KB`). Defaults to `None`.
    */
  def apply(blockSize: Option[Bzip2BlockSize] = None): Bzip2Compressor =
    new Bzip2Compressor(blockSize)

  /** Returns the compression pipeline of a [[Bzip2Compressor]] with the default block size, the same as
    * `Bzip2Compressor().compress`.
    */
  def compress: ZPipeline[Any, Throwable, Byte, Byte] = apply().compress
}

/** A [[Compressor]] that writes the Bzip2 format, using Apache Commons Compress
  * (`org.apache.commons:commons-compress`). Use it to produce Bzip2 data, such as the content of a `.bz2` file. Create
  * one with `Bzip2Compressor(blockSize)` to choose the block size, or use `Bzip2Compressor.compress` for the default of
  * 900KB. Read the result back with [[Bzip2Decompressor]].
  *
  * {{{
  * import zio._
  * import zio.compress._
  * import zio.stream._
  *
  * // compressed succeeds with 94 bytes that start with "BZh1": the Bzip2 magic bytes and block size 1 (100KB)
  * val compressed: Task[Chunk[Byte]] =
  *   ZStream
  *     .fromIterable(("Scala Cookbook, recipe 4.2\n" * 30).getBytes("UTF-8"))
  *     .via(Bzip2Compressor(blockSize = Some(Bzip2BlockSize.BlockSize100KB)).compress)
  *     .runCollect
  * }}}
  *
  * @see
  *   [[https://zio.dev/zio-streams-compress/reference-bzip2/ Bzip2]]
  */
final class Bzip2Compressor private (blockSize: Option[Bzip2BlockSize]) extends Compressor {

  override def compress(implicit trace: Trace): ZPipeline[Any, Throwable, Byte, Byte] =
    viaOutputStreamByte { outputStream =>
      blockSize match {
        case Some(bs) => new BZip2CompressorOutputStream(outputStream, bs.hundredKbIncrements)
        case None     => new BZip2CompressorOutputStream(outputStream)
      }
    }
}

/** Creates [[Bzip2Decompressor]]s: `apply` takes the options, and `decompress` gives the pipeline with the default
  * options.
  */
object Bzip2Decompressor {

  /** A [[Decompressor]] for Bzip2, based on the Apache Commons Compress library.
    *
    * @param chunkSize
    *   The maximum chunk size of the outgoing ZStream. Defaults to `ZStream.DefaultChunkSize` (4KiB).
    */
  def apply(chunkSize: Int = ZStream.DefaultChunkSize): Bzip2Decompressor =
    new Bzip2Decompressor(chunkSize)

  /** Returns the decompression pipeline of a [[Bzip2Decompressor]] with the default chunk size, the same as
    * `Bzip2Decompressor().decompress`.
    */
  def decompress: ZPipeline[Any, Throwable, Byte, Byte] = apply().decompress
}

/** A [[Decompressor]] that reads the Bzip2 format, using Apache Commons Compress
  * (`org.apache.commons:commons-compress`). Use it to read Bzip2 data, such as the content of a `.bz2` file or the
  * output of [[Bzip2Compressor]]. Create one with `Bzip2Decompressor(chunkSize)` to set the largest chunk it emits, or
  * use `Bzip2Decompressor.decompress` for the default of 4 KiB.
  *
  * Input that isn't in the Bzip2 format raises a `java.io.IOException` with the message
  * `Stream is not in the BZip2 format` when the stream is read, and the error does not reach the error channel. A
  * single-block input that is cut short raises a `java.io.IOException` with the message `Unexpected end of stream`,
  * which does not reach the error channel either.
  *
  * {{{
  * import zio._
  * import zio.compress._
  * import zio.stream._
  *
  * val authors: String = "Martin Odersky, Lex Spoon, Bill Venners"
  *
  * // roundTrip succeeds with "Martin Odersky, Lex Spoon, Bill Venners"
  * val roundTrip: Task[String] =
  *   ZStream
  *     .fromIterable(authors.getBytes("UTF-8"))
  *     .via(Bzip2Compressor.compress)
  *     .via(Bzip2Decompressor(chunkSize = 1024).decompress)
  *     .runCollect
  *     .map(bytes => new String(bytes.toArray, "UTF-8"))
  * }}}
  *
  * @see
  *   [[https://zio.dev/zio-streams-compress/reference-bzip2/ Bzip2]]
  */
final class Bzip2Decompressor private (chunkSize: Int) extends Decompressor {

  override def decompress(implicit trace: Trace): ZPipeline[Any, Throwable, Byte, Byte] =
    // BrotliInputStream.read does its best to read as many bytes as requested; no buffering needed.
    viaInputStreamByte(chunkSize)(new BZip2CompressorInputStream(_))
}
