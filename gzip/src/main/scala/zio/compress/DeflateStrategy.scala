package zio.compress

sealed trait DeflateStrategy extends Product with Serializable

object DeflateStrategy {
  case object Filtered extends DeflateStrategy
  case object HuffmanOnly extends DeflateStrategy
}
