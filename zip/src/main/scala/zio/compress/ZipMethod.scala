package zio.compress

sealed trait ZipMethod extends Product with Serializable

object ZipMethod {
  case object Stored extends ZipMethod
  case object Deflated extends ZipMethod
}
