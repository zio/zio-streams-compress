---
id: how-to-archive-a-single-file
title: "How to Wrap a Single File in an Archive"
sidebar_label: "Wrap a Single File in an Archive"
---

This guide packs one stream of bytes into a ZIP or Tar archive that holds a single named file, and unpacks such an archive back to plain bytes. The result behaves like a codec: a `Compressor` and a `Decompressor` that you can pass to any code written for codecs.

## The Problem

Some systems exchange single files inside an archive: a report download that must be a `.zip`, a data feed that arrives as a `.zip` with one CSV inside. The archive pipelines of this library work on streams of entries, `(ArchiveEntry, ZStream)` pairs, while the rest of a program usually works on plain bytes. Writing the wrapping by hand means building a one-element entry stream on the way in and picking the content of the first entry on the way out, in every place that needs it. `ArchiveSingleFileCompressor` and `ArchiveSingleFileDecompressor` do that once and expose the result as an ordinary codec.

## Add the Module

Add the module of the archive format. This guide uses ZIP:

```sbt
libraryDependencies += "dev.zio" %% "zio-streams-compress-zip" % "@VERSION@"
```

## Pack Bytes into a One-File ZIP

`ArchiveSingleFileCompressor.forName` takes an archiver and the name of the file inside the archive, and returns a `Compressor`. Its `compress` pipeline turns the incoming bytes into a ZIP archive with one entry of that name:

```scala mdoc:compile-only
import zio._
import zio.compress._
import zio.stream._

val zipReport: Compressor = ArchiveSingleFileCompressor.forName(ZipArchiver(), "report.csv")

def writeReport(rows: ZStream[Any, Throwable, Byte], output: String): Task[Long] =
  rows.via(zipReport.compress).run(ZSink.fromFileName(output))
```

Because `zipReport` is a `Compressor`, it fits every function that accepts one. The `compressFile` function of [How to Compress and Decompress a Stream](./how-to-compress-and-decompress.md) works with it unchanged, and so does any code that lets its caller choose between Gzip and ZIP output.

## Unpack a One-File Archive

`ArchiveSingleFileDecompressor` takes an unarchiver and returns a `Decompressor`. Its `decompress` pipeline emits the content of the first file in the archive. It skips directory entries before that file, and ignores every entry after it:

```scala mdoc:compile-only
import zio._
import zio.compress._
import zio.stream._

val unzipReport: Decompressor = ArchiveSingleFileDecompressor(ZipUnarchiver())

def readReport(input: String): Task[String] =
  ZStream.fromFileName(input).via(unzipReport.decompress).via(ZPipeline.utf8Decode).runFold("")(_ + _)
```

The name of the file is dropped, so use the decompressor only when the archive is known to hold one file, or when any file will do.

## Check the Round Trip

To see both sides work together, this check packs a CSV text in memory, unpacks it again, and compares the result with the original:

```scala mdoc:compile-only
import zio._
import zio.compress._
import zio.stream._

val zipReport: Compressor = ArchiveSingleFileCompressor.forName(ZipArchiver(), "report.csv")
val unzipReport: Decompressor = ArchiveSingleFileDecompressor(ZipUnarchiver())

val csv = "id,total\n1,42\n2,7\n"

val roundTrip: Task[Boolean] =
  ZStream
    .fromIterable(csv.getBytes("UTF-8"))
    .via(zipReport.compress)
    .via(unzipReport.decompress)
    .via(ZPipeline.utf8Decode)
    .runFold("")(_ + _)
    .map(_ == csv)
```

`roundTrip` succeeds with `true`: the text comes back unchanged.

## Use Tar or Zip4J Instead

`TarArchiver` and `Zip4JArchiver` need the size of the file before its content, so `forName` takes the size as a third argument for them. The size must match the bytes that pass through, or the stream stops with an `IllegalStateException`:

```scala mdoc:compile-only
import zio.compress._

def tarOf(name: String, size: Long): Compressor = ArchiveSingleFileCompressor.forName(TarArchiver(), name, size)

val untar: Decompressor = ArchiveSingleFileDecompressor(TarUnarchiver())
```

To set more than the name and size, such as the last-modified time, pass a complete `ArchiveEntry` to `ArchiveSingleFileCompressor.apply`. [Core Interfaces](./reference-core.md#archivesinglefilecompressor-and-archivesinglefiledecompressor) shows it. For a compressed Tar file, chain a codec after the archive: `bytes.via(tarOf(name, size).compress).via(GzipCompressor.compress)`.
