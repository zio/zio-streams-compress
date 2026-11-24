package zio.compress.zip

import zio.Chunk

/** How `ZipArchiver` writes the content of file entries: compressed (`Deflated`) or not compressed (`Stored`). */
sealed trait ZipMethod extends Product with Serializable

/** The two ZIP methods of `ZipArchiver`. */
object ZipMethod {

  /** Compresses each file entry with deflate. The default of `ZipArchiver`. */
  case object Deflated extends ZipMethod

  /** Writes each file entry without compression. */
  case object Stored extends ZipMethod

  /** Both methods: `Deflated` and `Stored`. */
  final val Values: Chunk[ZipMethod] = Chunk(Deflated, Stored)
}
