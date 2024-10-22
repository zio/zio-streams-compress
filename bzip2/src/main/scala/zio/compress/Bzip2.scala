package zio.compress

import org.apache.commons.compress.compressors.bzip2.{BZip2CompressorInputStream, BZip2CompressorOutputStream}
import zio.compress.JavaIoInterop.{viaInputStreamByte, viaOutputStreamByte}
import zio.stream._
import zio.Trace

object Bzip2Compressor {

  /** Make a pipeline that accepts a stream of bytes and produces a stream with Bzip2 compressed bytes.
    *
    * Note: Bzip2 uses a lot of memory. See
    * [[org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream]] for an overview of the required heap
    * size for each block size.
    *
    * @param blockSize
    *   the block size to use. Defaults to 900KB.
    */
  def make(blockSize: Option[Bzip2BlockSize] = None): Bzip2Compressor =
    new Bzip2Compressor(blockSize)
}

final class Bzip2Compressor private (blockSize: Option[Bzip2BlockSize]) extends Compressor {
  override def compress(implicit trace: Trace): ZPipeline[Any, Throwable, Byte, Byte] =
    viaOutputStreamByte { outputStream =>
      blockSize match {
        case Some(bs) => new BZip2CompressorOutputStream(outputStream, bs.hundredKbIncrements)
        case None     => new BZip2CompressorOutputStream(outputStream)
      }
    }
}

object Bzip2Decompressor {

  /** Makes a pipeline that accepts a Bzip2 compressed byte stream and produces a decompressed byte stream.
    *
    * @param chunkSize
    *   The maximum chunk size of the outgoing ZStream. Defaults to `ZStream.DefaultChunkSize` (4KiB).
    */
  def make(chunkSize: Int = ZStream.DefaultChunkSize): Bzip2Decompressor =
    new Bzip2Decompressor(chunkSize)
}

final class Bzip2Decompressor private (chunkSize: Int) extends Decompressor {
  override def decompress(implicit trace: Trace): ZPipeline[Any, Throwable, Byte, Byte] =
    // BrotliInputStream.read does its best to read as many bytes as requested; no buffering needed.
    viaInputStreamByte(chunkSize)(new BZip2CompressorInputStream(_))
}
