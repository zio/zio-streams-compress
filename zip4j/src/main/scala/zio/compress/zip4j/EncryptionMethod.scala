package zio.compress.zip4j

import zio.Chunk

/** How `Zip4JArchiver.encrypted` encrypts the file entries of an archive. */
sealed trait EncryptionMethod extends Product with Serializable

/** The three encryption methods of `Zip4JArchiver.encrypted`. */
object EncryptionMethod {

  /** Encrypted with the weak ZIP standard algorithm.
    */
  case object ZipStandard extends EncryptionMethod

  /** Encrypted with AES with 128-bit key length, not well-supported by other tools.
    */
  case object AES_128 extends EncryptionMethod

  /** Encrypted with AES with 256-bit key length, the strongest choice but not well-supported by other tools.
    */
  case object AES_256 extends EncryptionMethod

  /** All three methods: `ZipStandard`, `AES_128` and `AES_256`. */
  final val Values: Chunk[EncryptionMethod] = Chunk(ZipStandard, AES_128, AES_256)
}
