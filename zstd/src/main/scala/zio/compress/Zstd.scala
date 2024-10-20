package zio.compress

import com.github.luben.zstd.{ZstdInputStream, ZstdOutputStream}
import zio.compress.JavaIoInterop.{viaInputStreamByte, viaOutputStreamByte}
import zio.stream._

import java.io.BufferedInputStream

object ZstdCompressor {

  /**
   * Make a pipeline that accepts a stream of bytes and produces a stream with Zstd compressed bytes.
   *
   * @param level
   *   The compression level to use. Valid values: -22 to 22. Defaults to 3.
   * @param workers
   *   The number of worker threads to use for parallel compression. Defaults to no worker threads.
   */
  def make(
    level: Option[Int] = None,
    workers: Option[Int] = None,
  ): ZstdCompressor =
    new ZstdCompressor(level, workers)
}

class ZstdCompressor private (level: Option[Int], workers: Option[Int]) extends Compressor {
  override def compress: ZPipeline[Any, Throwable, Byte, Byte] =
    viaOutputStreamByte { outputStream =>
      val zstdOutputStream = new ZstdOutputStream(outputStream)
      level.foreach(zstdOutputStream.setLevel)
      workers.foreach(zstdOutputStream.setWorkers)
      zstdOutputStream
    }
}

object ZstdDecompressor {

  /**
   * Makes a pipeline that accepts a Zstd compressed byte stream and produces a decompressed byte stream.
   *
   * @param chunkSize
   *   The maximum chunk size of the outgoing ZStream. Defaults to `ZStream.DefaultChunkSize` (4KiB).
   */
  def make(chunkSize: Int = ZStream.DefaultChunkSize): ZstdDecompressor =
    new ZstdDecompressor(chunkSize)
}

class ZstdDecompressor private (chunkSize: Int) extends Decompressor {
  override def decompress: ZPipeline[Any, Throwable, Byte, Byte] =
    // ZstdInputStream.read does not try to read the requested number of bytes, but it does have a good
    // `available()` implementation, so with buffering we can still get full chunks.
    viaInputStreamByte(chunkSize) { inputStream =>
      new BufferedInputStream(new ZstdInputStream(inputStream), chunkSize)
    }
}
