package zio.compress

/** Default values shared by the modules. */
object Defaults {

  /** 64 KiB (65536 bytes). The default chunk size of the entry content streams of `ZipUnarchiver`, `TarUnarchiver` and
    * `Zip4JUnarchiver`, the default output chunk size of the archivers and of the compressors that write through the
    * output-stream bridge in `JavaIoInterop`, and the default buffer size of `GzipCompressor` and `GzipDecompressor`.
    */
  final val DefaultChunkSize: Int = 64 * 1024

  /** 2. The default capacity, in chunks, of the internal queue between a pipeline and the Java stream it wraps. */
  final val DefaultChunkedQueueSize: Int = 2
}
