package zio.compress

/** Thrown when an [[ArchiveEntry]] carries an underlying entry of a type that the format does not support, for example
  * a `java.util.zip.ZipEntry` given to `TarArchiver`. The message names the class of the value, for example
  * `Unsupported underlying entry of type java.util.zip.ZipEntry`, or ends in `null` for a `null` value. The archivers
  * do not catch it, so the archive stream dies with it.
  *
  * @param underlying
  *   the unsupported underlying value
  */
final case class IllegalUnderlyingEntry(underlying: Any)
    extends IllegalArgumentException(
      s"Unsupported underlying entry of type ${Option(underlying).fold("null")(_.getClass.getName)}"
    )
