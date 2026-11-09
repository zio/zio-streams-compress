---
id: reference-zip
title: "Zip"
sidebar_label: "Zip"
---

The `zip` module creates and reads ZIP archives. It is built on the JVM standard library's ZIP support. For password-protected
archives, use the [Zip4J](./reference-zip4j.md) module instead.

Add the module to your `build.sbt`:

```sbt
libraryDependencies += "dev.zio" %% "zio-streams-compress-zip" % "@VERSION@"
```

## ZipArchiver

`ZipArchiver` is an `Archiver` that writes ZIP archives. Its entries do not need a known size, so you can stream content of
unknown length. Use `ZipArchiver.apply` to set the compression level and method, or `ZipArchiver.archive` for the defaults:

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

`ZipUnarchiver#list` reads each entry's content to fill in the uncompressed size. To process the content itself, use
`ZipUnarchiver#unarchive` instead, and read each entry's content before the next entry is emitted. See
[Read Archives Safely](./how-to-read-archives-safely.md) for the rules.

## Options

| Option       | Type                             | Default                                  |
|--------------|----------------------------------|------------------------------------------|
| `level`      | `Option[DeflateCompressionLevel]` (writer) | `None` (zlib's default, level 6)  |
| `zipMethod`  | `Option[ZipMethod]` (writer)     | `None` (deflated)                        |
| `chunkSize`  | `Int` (reader)                   | 64 KiB                                   |

`level` applies only to deflated entries. `ZipMethod` has two values: `ZipMethod.Deflated` compresses each entry, and
`ZipMethod.Stored` writes each entry without compression.

## Known Issue: Stored Entries

`ZipMethod.Stored` does not work yet. The writer fails with `STORED entry missing size, compressed size, or crc-32` for every
entry, because the CRC-32 of the content is never computed. Use the default `ZipMethod.Deflated`, or set `level` to
`DeflateCompressionLevel.NoCompressionLevel` if you want deflated entries without compression.

## Notes

- The archive format has no encryption in this module. Use [Zip4J](./reference-zip4j.md) to read password-protected archives.
- The writer does not check the size of each entry, because the size is optional for this format.
- The reader's error type is `IOException` for the entry content, and `Throwable` for the archive structure.
