package zio.compress

import com.github.luben.zstd.{ZstdInputStream, ZstdOutputStream}
import zio.compress.JavaIoInterop.{viaInputStreamByte, viaOutputStreamByte}
import zio.stream._

import java.io.BufferedInputStream

object ZstdCompressor {

  /** Make a pipeline that accepts a stream of bytes and produces a stream with Zstd compressed bytes.
    *
    * @param level
    *   The compression level to use. Defaults to level 3.
    * @param workers
    *   The number of worker threads to use for parallel compression. Set to 0 to detect the number of CPU cores and use
    *   that number of worker threads. The actual number of worker threads is capped. Valid values: 0 and higher.
    *   Defaults to 1.
    * @param customDictionary
    *   a custom dictionary, or `None` for no custom dictionary
    */
  def make(
    level: Option[ZstdCompressionLevel] = None,
    workers: Option[Int] = None,
    customDictionary: Option[Array[Byte]] = None,
  ): ZstdCompressor =
    new ZstdCompressor(level, workers, customDictionary)
}

class ZstdCompressor private (
  level: Option[ZstdCompressionLevel],
  workers: Option[Int],
  customDictionary: Option[Array[Byte]],
) extends Compressor {
  override def compress: ZPipeline[Any, Throwable, Byte, Byte] =
    viaOutputStreamByte { outputStream =>
      val zstdOutputStream = new ZstdOutputStream(outputStream)
      level.foreach(l => zstdOutputStream.setLevel(l.level))
      workers.foreach(zstdOutputStream.setWorkers)
      customDictionary.foreach(zstdOutputStream.setDict)
      zstdOutputStream
    }
}

object ZstdDecompressor {

  /** Makes a pipeline that accepts a Zstd compressed byte stream and produces a decompressed byte stream.
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
