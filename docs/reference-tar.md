---
id: reference-tar
title: "Tar"
sidebar_label: "Tar"
---

The `tar` module creates and reads Tar archives. It is built on the Apache Commons Compress library. A tar archive bundles files but does not compress them, so a compressed tarball is a tar archive wrapped in a codec such as Gzip.

Add the module to your `build.sbt`:

```sbt
// zio-streams-compress-tar: Tar archiving and unarchiving
libraryDependencies += "dev.zio" %% "zio-streams-compress-tar" % "@VERSION@"
```

## TarArchiver

`TarArchiver` is an `Archiver` that writes Tar archives. Every entry must have a known uncompressed size, so the entry type is `ArchiveEntry[Some, _]`:

```scala mdoc:compile-only
import zio.compress._
import zio.stream._

val content = "Hello, ZIO!".getBytes("UTF-8")

val entry = ArchiveEntry[Option, Any]("notes/readme.txt").withKnownUncompressedSize(content.length.toLong)

val tar = ZStream((entry, ZStream.fromIterable(content))).via(TarArchiver().archive)
```

If the number of bytes written does not match the declared size, the stream dies with an `IllegalStateException`.

To write a compressed tarball, pipe the archive through a compressor. The example uses Gzip:

```scala mdoc:compile-only
import zio.compress._
import zio.stream._

val content = "Hello, ZIO!".getBytes("UTF-8")

val entry = ArchiveEntry[Option, Any]("notes/readme.txt").withKnownUncompressedSize(content.length.toLong)

val tarGz = ZStream((entry, ZStream.fromIterable(content)))
  .via(TarArchiver().archive)
  .via(GzipCompressor.compress)
```

## TarUnarchiver

`TarUnarchiver` is an `Unarchiver` that reads Tar archives. Each entry comes with its content as a stream. To read a compressed tarball, decompress first:

```scala mdoc:compile-only
import zio.compress._
import zio.stream._

val names = ZStream
  .fromFileName("archive.tar.gz")
  .via(GzipDecompressor.decompress)
  .via(TarUnarchiver().list)
  .map(_.name)
```

`TarUnarchiver#list` reads each entry's content to fill in the uncompressed size. `TarUnarchiver#unarchive` gives the content as a stream, and it must be fully read before the next entry is emitted. See [Read Archives Safely](./how-to-read-archives-safely.md).

The companion objects offer shortcuts with the default options: `TarArchiver.archive`, `TarUnarchiver.list` and `TarUnarchiver.unarchive` are the same pipelines as `TarArchiver().archive`, `TarUnarchiver().list` and `TarUnarchiver().unarchive`.

## Options

| Option      | Type               | Default |
|-------------|--------------------|---------|
| `chunkSize` | `Int` (unarchiver) | 64 KiB  |

`TarArchiver` takes no options.

## Entry Metadata

Tar entries carry a name, a size, and optional timestamps. `TarArchiver` writes the `lastModified`, `lastAccess`, and `creation` fields of an `ArchiveEntry` when they are set. Set `isDirectory` to `true` to write a directory entry, and the name gets a trailing `/`.

`TarUnarchiver` reads the name, the directory flag, the size and the three timestamps back. A size that the archive does not record is `None`.

The underlying entry is a commons-compress `TarArchiveEntry`, which also holds the Unix permissions, the owner and the group. Read it with `entry.underlying[TarArchiveEntry]` after `import zio.compress.Tar._`. To write those fields, attach a `TarArchiveEntry` with `ArchiveEntry#withUnderlying`. When its directory flag matches the `ArchiveEntry`, the writer copies that entry's header and then applies the name and the fields set on the `ArchiveEntry`; otherwise it starts from a fresh header. [How to Set Format-Specific Entry Metadata](./how-to-set-entry-metadata.md) shows how to set the permissions of an entry.

## Notes

- The archiver checks the size of each entry against the bytes it receives, and dies with an `IllegalStateException` on a mismatch.
- The unarchiver's error type is `IOException` for the entry content, and `Throwable` for the archive structure.
- Tar has no compression of its own. Use [Gzip](./reference-gzip.md) for `.tar.gz` files.
