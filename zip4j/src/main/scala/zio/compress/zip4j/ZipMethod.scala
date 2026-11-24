package zio.compress.zip4j

import zio.Chunk

/** How `Zip4JArchiver` writes the content of file entries: compressed (`Deflated`) or not compressed (`Stored`). This
  * is a different type from the `ZipMethod` of the zip module.
  */
sealed trait ZipMethod extends Product with Serializable

/** The two ZIP methods of `Zip4JArchiver`. */
object ZipMethod {

  /** Compresses each file entry with deflate. The default of `Zip4JArchiver`. */
  case object Deflated extends ZipMethod

  /** Writes each file entry without compression. */
  case object Stored extends ZipMethod

  /** Both methods: `Deflated` and `Stored`. */
  final val Values: Chunk[ZipMethod] = Chunk(Deflated, Stored)
}
