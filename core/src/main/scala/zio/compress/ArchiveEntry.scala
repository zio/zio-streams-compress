package zio.compress

import ArchiveEntry.ArchiveEntryToUnderlying

import java.time.Instant

/** Describes one entry of an archive: its name, whether it is a directory, its uncompressed size when known, its
  * timestamps, and optionally the entry object of the archive library underneath (the underlying entry).
  *
  * An [[Archiver]] takes pairs of an `ArchiveEntry` and the content stream of that entry, and an [[Unarchiver]] emits
  * such pairs. The `Size` type parameter tells whether the uncompressed size is known. Archivers that need the size
  * before the content, `TarArchiver` and `Zip4JArchiver`, accept only `ArchiveEntry[Some, _]`; build one with
  * `withKnownUncompressedSize`. The `with*` methods return a copy with one field changed; set `lastAccess` and
  * `creation` with `copy`.
  *
  * {{{
  * import zio.compress._
  * import java.time.Instant
  *
  * val report: ArchiveEntry[Option, Any] = ArchiveEntry[Option, Any]("reports/2024-q1.csv")
  *
  * val sized: ArchiveEntry[Some, Any] = report.withKnownUncompressedSize(2048L)
  * // sized.uncompressedSize == Some(2048L)
  *
  * val folder: ArchiveEntry[Option, Any] = ArchiveEntry[Option, Any]("reports").withIsDirectory(true)
  *
  * val stamped: ArchiveEntry[Option, Any] =
  *   report.withLastModified(Some(Instant.parse("2024-04-01T08:30:00Z")))
  * }}}
  *
  * @tparam Size
  *   `Some` when the uncompressed size is always present, `Option` when it may be missing, `None` when it is never
  *   present
  * @tparam Underlying
  *   the type of the underlying entry; `Any` for an entry built without one
  * @param name
  *   the path of the entry inside the archive. The archivers of the zip, zip4j and tar modules add a trailing `/` to
  *   the name of a directory entry and remove it from the name of a file entry.
  * @param uncompressedSize
  *   the number of bytes of the entry's content, when known
  * @param isDirectory
  *   `true` for a directory entry
  * @param lastModified
  *   the time the entry was last modified, when known
  * @param lastAccess
  *   the time the entry was last accessed, when known
  * @param creation
  *   the time the entry was created, when known
  * @param underlying
  *   the entry object of the archive library, see `withUnderlying`; `()` when there is none
  * @see
  *   [[https://zio.dev/zio-streams-compress/reference-core/ ZIO Streams Compress: core reference]]
  */
final case class ArchiveEntry[+Size[A] <: Option[A], +Underlying](
  name: String,
  uncompressedSize: Size[Long] = Option.empty[Long],
  isDirectory: Boolean = false,
  lastModified: Option[Instant] = None,
  lastAccess: Option[Instant] = None,
  creation: Option[Instant] = None,
  private val underlying: Underlying = (),
) {

  /** Returns a copy of this entry with the given name. */
  def withName(name: String): ArchiveEntry[Size, Underlying] = copy(name = name)

  /** Returns a copy of this entry with the given uncompressed size, typed with the size's type `S`. Pass
    * `Option.empty[Long]` to turn an `ArchiveEntry[Some, _]` back into an `ArchiveEntry[Option, _]`.
    */
  def withUncompressedSize[S[A] <: Option[A]](uncompressedSize: S[Long]): ArchiveEntry[S, Underlying] =
    copy(uncompressedSize = uncompressedSize)

  /** Returns a copy of this entry with the given uncompressed size, typed `ArchiveEntry[Some, Underlying]` as the
    * archivers that need the size require.
    */
  def withKnownUncompressedSize(uncompressedSize: Long): ArchiveEntry[Some, Underlying] =
    copy(uncompressedSize = Some(uncompressedSize))

  /** Returns a copy of this entry marked as a directory (`true`) or as a file (`false`). */
  def withIsDirectory(isDirectory: Boolean): ArchiveEntry[Size, Underlying] = copy(isDirectory = isDirectory)

  /** Returns a copy of this entry with the given last-modified time. */
  def withLastModified(lastModified: Option[Instant]): ArchiveEntry[Size, Underlying] =
    copy(lastModified = lastModified)

  /** Returns a copy of this entry that carries `underlying` as its underlying entry: an entry object of the archive
    * library, such as a `java.util.zip.ZipEntry` for `ZipArchiver` or a commons-compress `TarArchiveEntry` for
    * `TarArchiver`. The archiver starts from a copy of that object, so it keeps the format-specific fields the object
    * holds, except the compression method (and, for `Zip4JArchiver`, the encryption settings), which the archiver sets
    * itself; it then applies the name, the size and those timestamps of this entry that the format records.
    * `ZipArchiver` uses the object only when its name and directory flag match this entry, and `TarArchiver` only when
    * its directory flag matches; otherwise they start from a fresh object. An underlying value of a type the archiver
    * does not support makes the archive stream die with an [[IllegalUnderlyingEntry]].
    */
  def withUnderlying[U](underlying: U): ArchiveEntry[Size, U] = copy(underlying = underlying)

  /** Returns the underlying entry as a `U`, converted by the implicit `ArchiveEntryToUnderlying[U]`.
    *
    * The zip, tar and zip4j modules provide these conversions in the objects `Zip`, `Tar` and `Zip4J`; import the one
    * for your format. When this entry has no underlying value, the conversions to `ZipEntry`, `TarArchiveEntry` and
    * `ZipParameters` build one from the fields of this entry, and the conversion to `LocalFileHeader` returns an empty
    * header. These conversions throw an [[IllegalUnderlyingEntry]] when the underlying value has a type they do not
    * support.
    */
  def underlying[U](implicit U: ArchiveEntryToUnderlying[U]): U =
    U.underlying(this, underlying)
}

/** Companion of [[ArchiveEntry]], with the type classes that convert between an `ArchiveEntry` and the entry objects of
  * archive libraries.
  */
object ArchiveEntry {

  /** Builds an `ArchiveEntry` from an entry object of an archive library, with the implicit
    * `ArchiveEntryFromUnderlying[Size, U]`, and keeps that object as the underlying entry. The result is typed
    * `ArchiveEntry[Option, U]`, whatever `Size` the conversion has.
    */
  def fromUnderlying[Size[A] <: Option[A], U](underlying: U)(implicit
    U: ArchiveEntryFromUnderlying[Size, U]
  ): ArchiveEntry[Option, U] =
    U.archiveEntry(underlying)

  /** Converts an [[ArchiveEntry]] into an entry object of an archive library. `ArchiveEntry#underlying` and the
    * archivers use it. The zip, tar and zip4j modules provide instances in the objects `Zip`, `Tar` and `Zip4J`;
    * implement it only to support another archive library.
    *
    * @tparam Underlying
    *   the entry type of the archive library
    */
  trait ArchiveEntryToUnderlying[+Underlying] extends Serializable {

    /** Returns the `Underlying` object for `entry`. `underlying` is the value that `entry` carries, `()` when it
      * carries none. The instances in `Zip`, `Tar` and `Zip4J` throw an [[IllegalUnderlyingEntry]] when `underlying`
      * has a type they do not support.
      */
    def underlying[S[A] <: Option[A]](entry: ArchiveEntry[S, Any], underlying: Any): Underlying
  }

  /** Builds an [[ArchiveEntry]] from an entry object of an archive library. The unarchivers use it for every entry they
    * emit. The zip, tar and zip4j modules provide instances in the objects `Zip`, `Tar` and `Zip4J`; implement it only
    * to support another archive library.
    *
    * @tparam Size
    *   whether the built entries have a known uncompressed size
    * @tparam Underlying
    *   the entry type of the archive library
    */
  trait ArchiveEntryFromUnderlying[+Size[A] <: Option[A], Underlying] extends Serializable {

    /** Builds an `ArchiveEntry` from `underlying`, and keeps `underlying` as its underlying entry. */
    def archiveEntry(underlying: Underlying): ArchiveEntry[Size, Underlying]
  }
}
