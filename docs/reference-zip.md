---
id: reference-zip
title: "Zip"
sidebar_label: "Zip"
---

The `zip` module creates and reads ZIP archives. It is built on the JVM standard library's ZIP support. For password-protected archives, use the [Zip4J](./reference-zip4j.md) module instead. Also use the Zip4J module when you need `ZipMethod.Stored` and the archive entries are large, as this module needs to pull the full content of each entry in memory to calculate a checksum.

Add the module to your `build.sbt`:

```sbt
// zio-streams-compress-zip: Zip archiving and unarchiving
libraryDependencies += "dev.zio" %% "zio-streams-compress-zip" % "@VERSION@"
```

## ZipArchiver

`ZipArchiver` is an `Archiver` that writes ZIP archives. Its entries do not need a known size, so you can stream content of unknown length. Use `ZipArchiver.apply` to set the compression level and method, or `ZipArchiver.archive` for the defaults:

```scala mdoc:compile-only
import zio.compress._
import zio.stream._

val files = ZStream.fromIterable(
  Seq(
    ArchiveEntry[Option, Any]("notes/readme.txt") -> ZStream.fromIterable("Hello, ZIO!".getBytes("UTF-8")),
    ArchiveEntry[Option, Any]("notes", isDirectory = true) -> ZStream.empty,
  )
)

val archive: ZStream[Any, Throwable, Byte] = files.via(ZipArchiver().archive)
```

Set `isDirectory` to `true` to write a directory entry; the writer adds the trailing `/` to the name. A name that ends in `/` without `isDirectory` is written as a file, with the slash removed.

## ZipUnarchiver

`ZipUnarchiver` is an `Unarchiver` that reads ZIP archives. Each entry comes with its content as a stream:

```scala mdoc:compile-only
import zio.compress._
import zio.stream._

val entries: ZStream[Any, Throwable, (String, Long)] =
  ZStream
    .fromFileName("archive.zip")
    .via(ZipUnarchiver().list)
    .map(entry => entry.name -> entry.uncompressedSize.getOrElse(-1L))
```

`ZipUnarchiver#list` reads each entry's content to fill in the uncompressed size. To process the content itself, use `ZipUnarchiver#unarchive` instead, and read each entry's content before the next entry is emitted. See [Read Archives Safely](./how-to-read-archives-safely.md) for the rules.

The companion objects offer shortcuts with the default options: `ZipArchiver.archive`, `ZipUnarchiver.list` and `ZipUnarchiver.unarchive` are the same pipelines as `ZipArchiver().archive`, `ZipUnarchiver().list` and `ZipUnarchiver().unarchive`.

## Options

| Option      | Type                                         | Default                          |
|-------------|----------------------------------------------|----------------------------------|
| `level`     | `Option[DeflateCompressionLevel]` (archiver) | `None` (zlib's default, level 6) |
| `zipMethod` | `ZipMethod` (archiver)                       | `Deflated`                       |
| `chunkSize` | `Int` (unarchiver)                           | 64 KiB                           |

`level` applies only to deflated entries. `ZipMethod` has two values: `ZipMethod.Deflated` compresses each entry, and `ZipMethod.Stored` writes each entry without compression. `ZipMethod.Values` lists both.

`ZipMethod` lives in the package `zio.compress.zip`, so it needs its own import. To write a deflated archive at the best compression level, and a stored archive:

```scala mdoc:compile-only
import zio.compress._
import zio.compress.zip._

val best = ZipArchiver(level = Some(DeflateCompressionLevel.BestCompressionLevel))

val stored = ZipArchiver(zipMethod = ZipMethod.Stored)
```

The Zip4J module has its own `ZipMethod` in the package `zio.compress.zip4j`. The two types are not interchangeable.

## Stored Entries

`ZipMethod.Stored` only works in v3.0.0 and later. Earlier versions produce broken ZIP files.

Zip support from the JVM requires a CRC32 to be calculated up front. To do this, the full content of each entry is pulled into memory. The Zip4J module does not do this, and will have lower memory usage.

## Entry Metadata

`ZipArchiver` writes the `lastModified`, `lastAccess` and `creation` fields of an `ArchiveEntry` when they are set, and `ZipUnarchiver` reads them back when the archive records them. A directory entry is always written stored, with size 0.

The underlying entry is a `java.util.zip.ZipEntry`. Read it with `entry.underlying[ZipEntry]` after `import zio.compress.Zip._`, for example to get the compression method with `ZipEntry#getMethod`. Fields that ZIP keeps only in the central directory at the end of the archive, such as the entry comment, are not available, because the unarchiver reads the archive as a stream. To write ZIP-specific fields, attach a `ZipEntry` with `ArchiveEntry#withUnderlying`; it is kept only when its name and directory flag match the `ArchiveEntry`. [Core Interfaces](./reference-core.md#underlying-entries) describes underlying entries.

## Notes

- The archive format has no encryption in this module. Use [Zip4J](./reference-zip4j.md) to read password-protected archives.
- The archiver does not check the size of each entry, because the size is optional for this format.
- The unarchiver's error type is `IOException` for the entry content, and `Throwable` for the archive structure.
