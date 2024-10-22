package zio.compress

import org.apache.commons.compress.compressors.bzip2.{BZip2CompressorInputStream, BZip2CompressorOutputStream}
import zio.compress.JavaIoInterop.{viaInputStreamByte, viaOutputStreamByte}
import zio.stream._
import zio.Trace

object Bzip2Compressor {

  /** A [[Compressor]] for Bzip2, based on the Apache Commons Compress library.
    *
    * Note: Bzip2 uses a lot of memory. See
    * [[org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream]] for an overview of the required heap
    * size for each block size.
    *
    * @param blockSize
    *   the block size to use. Defaults to 900KB.
    */
  def apply(blockSize: Option[Bzip2BlockSize] = None): Bzip2Compressor =
    new Bzip2Compressor(blockSize)

  /** See [[apply]] and [[Compressor.compress]]. */
  def compress: ZPipeline[Any, Throwable, Byte, Byte] = apply().compress
}

final class Bzip2Compressor private (blockSize: Option[Bzip2BlockSize]) extends Compressor {

  /** @inheritdoc */
  override def compress(implicit trace: Trace): ZPipeline[Any, Throwable, Byte, Byte] =
    viaOutputStreamByte { outputStream =>
      blockSize match {
        case Some(bs) => new BZip2CompressorOutputStream(outputStream, bs.hundredKbIncrements)
        case None     => new BZip2CompressorOutputStream(outputStream)
      }
    }
}

object Bzip2Decompressor {

  /** A [[Decompressor]] for Bzip2, based on the Apache Commons Compress library.
    *
    * @param chunkSize
    *   The maximum chunk size of the outgoing ZStream. Defaults to `ZStream.DefaultChunkSize` (4KiB).
    */
  def apply(chunkSize: Int = ZStream.DefaultChunkSize): Bzip2Decompressor =
    new Bzip2Decompressor(chunkSize)

  /** See [[apply]] and [[Decompressor.decompress]]. */
  def decompress: ZPipeline[Any, Throwable, Byte, Byte] = apply().decompress
}

final class Bzip2Decompressor private (chunkSize: Int) extends Decompressor {

  /** @inheritdoc */
  override def decompress(implicit trace: Trace): ZPipeline[Any, Throwable, Byte, Byte] =
    // BrotliInputStream.read does its best to read as many bytes as requested; no buffering needed.
    viaInputStreamByte(chunkSize)(new BZip2CompressorInputStream(_))
}
