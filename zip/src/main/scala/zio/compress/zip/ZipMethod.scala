package zio.compress.zip

import zio.Chunk

sealed trait ZipMethod extends Product with Serializable

object ZipMethod {
  case object Deflated extends ZipMethod
  case object Stored extends ZipMethod

  final val Values: Chunk[ZipMethod] = Chunk(Deflated, Stored)
}
