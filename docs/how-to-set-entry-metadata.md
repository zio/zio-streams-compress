---
id: how-to-set-entry-metadata
title: "How to Set Format-Specific Entry Metadata"
sidebar_label: "Set Format-Specific Entry Metadata"
---

This guide writes a Tar archive whose entries carry Unix permissions, so that a shell script stays executable after the archive is unpacked. It uses the underlying entry of `ArchiveEntry`, which reaches every field of the format, not only the common ones.

## The Problem

`ArchiveEntry` models the fields that every archive format shares: the name, the size, the directory flag and three timestamps. Formats have more. A Tar entry has a Unix mode, an owner and a group; a ZIP entry written by Zip4J has a file comment. A Tar archive written with only the common fields gives every file the permissions `644`, so a script packed with `TarArchiver` loses its execute bit. Every consumer then has to repair the permissions after unpacking:

```scala mdoc:compile-only
import java.nio.file.{Files, Path, Paths}
import java.nio.file.attribute.PosixFilePermissions

def makeExecutable(script: String): Path =
  Files.setPosixFilePermissions(Paths.get(script), PosixFilePermissions.fromString("rwxr-xr-x"))
```

A deployment that cannot run that extra step cannot run the script. The underlying entry of `ArchiveEntry` sets the mode while the archive is written.

## Add the Module

Add the Tar module to your `build.sbt`:

```sbt
libraryDependencies += "dev.zio" %% "zio-streams-compress-tar" % "@VERSION@"
```

The Tar module brings in Apache Commons Compress, whose `TarArchiveEntry` is the underlying entry type.

## Set the Common Fields First

Start with the fields `ArchiveEntry` models itself, because every format reads those. Here, the script gets a name, a size and a last-modified time:

```scala mdoc:silent
import zio._
import zio.compress._
import zio.compress.Tar._
import zio.stream._
import org.apache.commons.compress.archivers.tar.TarArchiveEntry

import java.time.Instant

def scriptEntry(script: Array[Byte]): ArchiveEntry[Some, Any] =
  ArchiveEntry[Option, Any]("bin/hello.sh")
    .withKnownUncompressedSize(script.length.toLong)
    .withLastModified(Some(Instant.parse("2024-01-01T00:00:00Z")))
```

## Attach the Tar Header

To set the mode, create a `TarArchiveEntry`, set the fields that `ArchiveEntry` does not model, and attach it with `ArchiveEntry#withUnderlying`. `TarArchiver` copies its header and then applies the name, size and timestamps of the `ArchiveEntry`, so only the extra fields matter on the `TarArchiveEntry`:

```scala mdoc:silent
def executable(entry: ArchiveEntry[Some, Any]): ArchiveEntry[Some, Any] = {
  val header = new TarArchiveEntry(entry.name)
  header.setMode(Integer.parseInt("755", 8))
  header.setUserName("deploy")
  entry.withUnderlying(header)
}
```

The directory flag of the `TarArchiveEntry` must match the `ArchiveEntry`. A `TarArchiveEntry` created from a name without a trailing `/` is a file, which matches here. When the two flags differ, the archiver drops the header and the extra fields are lost.

## Write and Read Back the Archive

With the header attached, the entry goes through `TarArchiver` like any other. To check the result, this program writes the archive in memory, reads it back with `TarUnarchiver#list`, and prints the mode of each entry from its underlying `TarArchiveEntry`:

```scala mdoc:compile-only
val script = "#!/bin/sh\necho hello\n".getBytes("UTF-8")

val modes: Task[Chunk[String]] =
  ZStream(executable(scriptEntry(script)) -> ZStream.fromIterable(script))
    .via(TarArchiver().archive)
    .via(TarUnarchiver().list)
    .map { entry =>
      val header = entry.underlying[TarArchiveEntry]
      s"${entry.name} mode=${Integer.toOctalString(header.getMode)} user=${header.getUserName}"
    }
    .runCollect
```

`modes` yields `bin/hello.sh mode=755 user=deploy`: the script keeps its mode and its owner.

`entry.underlying[TarArchiveEntry]` compiles only with `import zio.compress.Tar._` in scope, because the conversion it needs lives in the `Tar` object.

## Other Formats

The same pattern works for the other archive modules, with their own underlying types and imports. `ZipArchiver` accepts a `java.util.zip.ZipEntry`, with `import zio.compress.Zip._` to read one back. It keeps the `ZipEntry` only when its name and directory flag match the `ArchiveEntry`. `Zip4JArchiver` accepts a Zip4J `ZipParameters`, for example with a file comment, and applies its own compression and encryption settings on top. `Zip4JUnarchiver` returns a `LocalFileHeader` instead. Both need `import zio.compress.Zip4J._`.

[Core Interfaces](./reference-core.md#underlying-entries) describes underlying entries and the `IllegalUnderlyingEntry` error that an archiver raises for an underlying value of the wrong type.
