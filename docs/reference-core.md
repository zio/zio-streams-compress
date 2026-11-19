---
id: reference-core
title: "Core Interfaces"
sidebar_label: "Core"
---

The `core` module defines the interfaces that every codec and archive format in ZIO Streams Compress implements. Each codec module depends on `core`, so these types are always available once you add any other module from the [installation instructions](./index.md#installation).

Most users never implement these interfaces. They are useful for understanding what the codecs have in common and for writing code that accepts any codec.

## Compressor and Decompressor

A `Compressor` turns a raw byte stream into a compressed byte stream. A `Decompressor` does the reverse. Both expose a single pipeline:

```scala
trait Compressor extends Serializable {
  def compress: ZPipeline[Any, Throwable, Byte, Byte]
}

trait Decompressor extends Serializable {
  def decompress: ZPipeline[Any, Throwable, Byte, Byte]
}
```

Because every codec exposes the same shape, a function can accept any compressor:

```scala mdoc:compile-only
import zio.compress._
import zio.stream._

def compressText(compressor: Compressor, text: String): ZStream[Any, Throwable, Byte] =
  ZStream.fromIterable(text.getBytes("UTF-8")).via(compressor.compress)
```

`Compressor.empty` and `Decompressor.empty` pass bytes through unchanged. They are useful as a default when the caller does not want compression:

```scala mdoc:compile-only
import zio.compress._

val compressorOrNone: Compressor = Compressor.empty
```

## Archiver and Unarchiver

An `Archiver` takes a stream of archive entries and produces the bytes of an archive. Each entry is a pair of an `ArchiveEntry` and the content stream for that entry:

```scala
trait Archiver[-Size[A] <: Option[A]] extends Serializable {
  def archive: ZPipeline[
    Any,
    Throwable,
    (ArchiveEntry[Size, Any], ZStream[Any, Throwable, Byte]),
    Byte,
  ]
}
```

An `Unarchiver` does the reverse. It takes the bytes of an archive and produces the entries with their content:

```scala
trait Unarchiver[Size[A] <: Option[A], Underlying] extends Serializable {
  def unarchive: ZPipeline[
    Any,
    Throwable,
    Byte,
    (ArchiveEntry[Size, Underlying], ZStream[Any, Throwable, Byte]),
  ]

  def list: ZPipeline[Any, Throwable, Byte, ArchiveEntry[Some, Underlying]]
}
```

`Unarchiver#list` has a default implementation on top of `unarchive`. It returns the entries with their uncompressed size filled in. It reads each entry's content to count the bytes, so it is slower than `unarchive` on large archives. See [Read Archives Safely](./how-to-read-archives-safely.md) for the rules that apply to `unarchive`.

## ArchiveEntry

`ArchiveEntry` describes one entry in an archive:

```scala
final case class ArchiveEntry[+Size[A] <: Option[A], +Underlying](
  name: String,
  uncompressedSize: Size[Long] = Option.empty[Long],
  isDirectory: Boolean = false,
  lastModified: Option[Instant] = None,
  lastAccess: Option[Instant] = None,
  creation: Option[Instant] = None,
  private val underlying: Underlying = (),
)
```

The type parameter `Size` says whether the uncompressed size is known:

| Size     | Meaning                                         | Used by                                                   |
|----------|-------------------------------------------------|-----------------------------------------------------------|
| `Some`   | The uncompressed size is always present         | Writing with `Zip4JArchiver` and `TarArchiver`            |
| `None`   | The uncompressed size is never present          | No built-in module writes it                              |
| `Option` | The uncompressed size may or may not be present | Writing with `ZipArchiver`; reading with every unarchiver |

Create an entry with its name, and optionally its size and other fields. The `with*` methods copy an entry with one field changed:

```scala mdoc:compile-only
import zio.compress._
import java.time.Instant

val withoutSize: ArchiveEntry[Option, Any] = ArchiveEntry[Option, Any]("notes/readme.txt")

val withSize: ArchiveEntry[Some, Any] =
  ArchiveEntry[Option, Any]("notes/readme.txt").withKnownUncompressedSize(12L)

val dir: ArchiveEntry[Option, Any] =
  ArchiveEntry[Option, Any]("notes").withIsDirectory(true)

val timestamped: ArchiveEntry[Option, Any] =
  withoutSize.withLastModified(Some(Instant.parse("2024-01-01T00:00:00Z")))

val renamed: ArchiveEntry[Option, Any] = withoutSize.withName("docs/readme.txt")
```

The copy methods are:

- `ArchiveEntry#withName` replaces the name.
- `ArchiveEntry#withUncompressedSize` replaces the size and its `Size` type, for example `withUncompressedSize(Option.empty[Long])` to go back to `ArchiveEntry[Option, _]`.
- `ArchiveEntry#withKnownUncompressedSize` sets a known size and returns an `ArchiveEntry[Some, _]`.
- `ArchiveEntry#withIsDirectory` marks the entry as a directory or a file.
- `ArchiveEntry#withLastModified` replaces the last-modified time.
- `ArchiveEntry#withUnderlying` attaches a format-specific entry object, see [Underlying Entries](#underlying-entries).

There is no `with*` method for `lastAccess` and `creation`. Set them with `ArchiveEntry#copy`, for example `entry.copy(lastAccess = Some(instant))`.

## Underlying Entries

Each archive format has its own entry type in the library it is built on, with fields that `ArchiveEntry` does not model, such as Unix permissions in Tar or the encryption settings of Zip4J. `ArchiveEntry` keeps that object as its *underlying* entry, so you can read those fields from an archive and set them when you write one. You rarely need it; the common fields of `ArchiveEntry` cover most uses.

The underlying type depends on the module and on the direction:

- `ZipUnarchiver` emits entries whose underlying value is a `java.util.zip.ZipEntry`, and `ZipArchiver` accepts one.
- `TarUnarchiver` emits entries whose underlying value is a commons-compress `TarArchiveEntry`, and `TarArchiver` accepts one.
- `Zip4JUnarchiver` emits entries whose underlying value is a Zip4J `LocalFileHeader`. `Zip4JArchiver` accepts a Zip4J `ZipParameters` instead.

`ArchiveEntry#underlying[U]` returns the underlying value as a `U`. It needs an implicit `ArchiveEntry.ArchiveEntryToUnderlying[U]`, and these implicits live in the objects `Zip`, `Tar` and `Zip4J`, not in the companion of `ArchiveEntry`. Import the one for your format:

```scala mdoc:compile-only
import zio.compress._
import zio.compress.Tar._
import zio.stream._
import org.apache.commons.compress.archivers.tar.TarArchiveEntry

val modes: ZStream[Any, Throwable, (String, Int)] =
  ZStream
    .fromFileName("archive.tar")
    .via(TarUnarchiver().list)
    .map(entry => entry.name -> entry.underlying[TarArchiveEntry].getMode)
```

Calling `ArchiveEntry#underlying` on an entry you built yourself, without an underlying value, still works. For `ZipEntry`, `TarArchiveEntry` and `ZipParameters`, the format creates a fresh object from the `ArchiveEntry` fields. For `LocalFileHeader`, it returns an empty header.

When writing, `ArchiveEntry#withUnderlying` attaches the format's entry object. The archiver starts from that object and then applies the name, size and timestamps of the `ArchiveEntry`, so the fields of the `ArchiveEntry` win. The underlying object is dropped, and a fresh one is used, when it no longer matches the entry: `ZipArchiver` drops it when the name or the directory flag differs, and `TarArchiver` when the directory flag differs. [How to Set Format-Specific Entry Metadata](./how-to-set-entry-metadata.md) shows this for Tar permissions. If the underlying value has a type the archiver does not support, for example a `ZipEntry` passed to `TarArchiver`, the archiver fails with an `IllegalUnderlyingEntry`, an `IllegalArgumentException` that names the unsupported class.

The conversions in both directions are the type classes `ArchiveEntry.ArchiveEntryToUnderlying[U]` and `ArchiveEntry.ArchiveEntryFromUnderlying[Size, U]`. `ArchiveEntry.fromUnderlying` builds an `ArchiveEntry` from a format's entry object with the second one. You only need them to add support for another archive library.

## Size Requirements on Write

Some archive formats must know the size of each entry before its content is written. The `Size` type parameter enforces this at compile time:

- `Zip4JArchiver` and `TarArchiver` require `ArchiveEntry[Some, _]`. Use `ArchiveEntry#withKnownUncompressedSize` or `ArchiveEntry[Some, Any](name, Some(size))`.
- `ZipArchiver` accepts `ArchiveEntry[Option, _]`, so the size may be omitted.

The writers that check sizes compare the number of bytes actually written with the declared size. A mismatch kills the stream with an `IllegalStateException`, which ZIO reports as a defect rather than a typed error. The check is a pipeline you can also apply yourself: `Archiver.checkUncompressedSize`.

## ArchiveSingleFileCompressor and ArchiveSingleFileDecompressor

These two helpers turn an archiver or unarchiver into a `Compressor` or `Decompressor` for a stream that holds exactly one file. They are useful for formats such as a zipped single file where the caller wants a plain byte pipeline. [How to Wrap a Single File in an Archive](./how-to-archive-a-single-file.md) walks through a complete example.

`ArchiveSingleFileCompressor.forName` wraps a file name, with the size when the archiver requires it:

```scala mdoc:compile-only
import zio.compress._

val zipOneFile = ArchiveSingleFileCompressor.forName(ZipArchiver(), "data.csv")

val tarOneFile = ArchiveSingleFileCompressor.forName(TarArchiver(), "data.csv", 1024L)
```

To set more than the name and size, for example the last-modified time, pass a full `ArchiveEntry` to `ArchiveSingleFileCompressor.apply`:

```scala mdoc:compile-only
import zio.compress._
import java.time.Instant

val stampedZip = ArchiveSingleFileCompressor(
  ZipArchiver(),
  ArchiveEntry[Option, Any]("data.csv").withLastModified(Some(Instant.parse("2024-01-01T00:00:00Z"))),
)
```

`ArchiveSingleFileDecompressor` takes an unarchiver and emits the content of the first non-directory entry. It skips directory entries, ignores every entry after the first file, and drops the name, so use it only when the archive is known to hold one file. This example wraps a ZIP unarchiver:

```scala mdoc:compile-only
import zio.compress._

val unzipOneFile = ArchiveSingleFileDecompressor(ZipUnarchiver())
```

## Chunk Sizes and Defaults

The `Defaults` object holds the shared values for the modules. `Defaults.DefaultChunkSize` is 64 KiB. It is the default buffer size for the gzip compressor and decompressor, and the chunk size for `ZipUnarchiver`, `TarUnarchiver`, and `Zip4JUnarchiver`. `Defaults.DefaultChunkedQueueSize` is 2. It is the size of the internal queue between a pipeline and the Java stream it wraps.

Some codecs use ZIO's own default of 4 KiB (`ZStream.DefaultChunkSize`) for their decompressors. Each reference page states the default for its module.

## Compression Levels

`DeflateCompressionLevel` is shared by the gzip and zip modules. `DeflateCompressionLevel.apply` returns an `Option`, because only levels 0 to 9 are valid:

```scala mdoc:compile-only
import zio.compress._

val level: Option[DeflateCompressionLevel] = DeflateCompressionLevel(9)

val invalid: Option[DeflateCompressionLevel] = DeflateCompressionLevel(10)

val best: DeflateCompressionLevel = DeflateCompressionLevel.BestCompressionLevel
```

The constants `CompressionLevel0` to `CompressionLevel9` name each level, and `DeflateCompressionLevel.Values` lists all ten. Four aliases name the common choices:

- `NoCompressionLevel` is level 0, which stores the data without compressing it.
- `FastestCompressionLevel` is level 1.
- `DefaultCompressionLevel` is level 5.
- `BestCompressionLevel` is level 9.

Leaving the level as `None` is not the same as passing `DefaultCompressionLevel`. With `None`, `GzipCompressor` and `ZipArchiver` use zlib's own default, which is level 6.
