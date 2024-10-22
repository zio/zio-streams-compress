package zio.compress

object Defaults {
  final val DefaultChunkSize: Int = 64 * 1024
  final val DefaultChunkedQueueSize: Int = 2
}
