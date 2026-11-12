package zio.compress

final case class IllegalUnderlyingEntry(underlying: Any)
    extends IllegalArgumentException(
      s"Unsupported underlying entry of type ${Option(underlying).fold("null")(_.getClass.getName)}"
    )
