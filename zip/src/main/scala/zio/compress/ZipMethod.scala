package zio.compress

sealed trait ZipMethod

object ZipMethod {
  case object Stored extends ZipMethod
  case object Deflated extends ZipMethod
}
