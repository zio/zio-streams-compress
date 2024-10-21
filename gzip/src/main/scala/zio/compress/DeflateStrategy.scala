package zio.compress

sealed trait DeflateStrategy

object DeflateStrategy {
  case object Filtered extends DeflateStrategy
  case object HuffmanOnly extends DeflateStrategy
}
