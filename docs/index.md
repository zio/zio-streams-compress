---
id: index
title: "Introduction to ZIO Streams Compress"
sidebar_label: "Introduction"
---

[ZIO Streams Compress](https://github.com/zio/zio-streams-compress) integrates several compression algorithms and archive formats with [ZIO Streams](https://zio.dev). Every codec and archive format is a pipeline, so it fits into a stream with `ZStream#via` like any other operator.

@PROJECT_BADGES@ [![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)

## Installation

The library is published for Scala 3, 2.13 and 2.12, and runs on the JVM only. Scala.js and Scala Native are not supported; PRs for them are welcome. As of 2.1.3, it requires ZIO 2.1.25 or later.

The `core` module holds the shared interfaces. Every codec and archive module in [Modules](#modules) depends on it and pulls it in, so you rarely add it on its own:

```sbt
libraryDependencies ++= Seq(
  // Compressor, Decompressor, Archiver, Unarchiver and ArchiveEntry interfaces. JVM only.
  "dev.zio" %% "zio-streams-compress-core" % "@VERSION@"
)
```

## Modules

Each format lives in its own module, so a project only pulls in the third-party libraries of the formats it uses. Add the modules you need.

### Compression Codecs

A codec compresses or decompresses one stream of bytes:

```sbt
libraryDependencies ++= Seq(
  // Gzip compressor and decompressor, built on the JDK. JVM only.
  "dev.zio" %% "zio-streams-compress-gzip" % "@VERSION@",

  // Zstandard compressor and decompressor, built on zstd-jni (native). JVM only.
  "dev.zio" %% "zio-streams-compress-zstd" % "@VERSION@",

  // Brotli decompressor only, built on the pure-Java Brotli decoder. JVM only.
  "dev.zio" %% "zio-streams-compress-brotli" % "@VERSION@",

  // Brotli compressor and decompressor, built on Brotli4J (native). JVM only.
  "dev.zio" %% "zio-streams-compress-brotli4j" % "@VERSION@",

  // Bzip2 compressor and decompressor, built on Apache Commons Compress. JVM only.
  "dev.zio" %% "zio-streams-compress-bzip2" % "@VERSION@",

  // LZ4 frame compressor and decompressor, built on lz4-java. JVM only.
  "dev.zio" %% "zio-streams-compress-lz4" % "@VERSION@",

  // Snappy compressor and decompressor (framed, basic and Hadoop formats), built on snappy-java. JVM only.
  "dev.zio" %% "zio-streams-compress-snappy" % "@VERSION@"
)
```

Each codec has a reference page: [Gzip](./reference-gzip.md), [Zstd](./reference-zstd.md), [Brotli](./reference-brotli.md), [Brotli4J](./reference-brotli4j.md), [Bzip2](./reference-bzip2.md), [LZ4](./reference-lz4.md) and [Snappy](./reference-snappy.md). [Choosing a Codec or Archive Format](./choosing-a-codec.md) compares them.

### Archive Formats

An archive format bundles many named entries into one stream of bytes:

```sbt
libraryDependencies ++= Seq(
  // ZIP archiver and unarchiver, built on the JDK; entry sizes may be unknown. JVM only.
  "dev.zio" %% "zio-streams-compress-zip" % "@VERSION@",

  // ZIP archiver and unarchiver with password protection (ZIP standard and AES), built on Zip4J. JVM only.
  "dev.zio" %% "zio-streams-compress-zip4j" % "@VERSION@",

  // Tar archiver and unarchiver, built on Apache Commons Compress. JVM only.
  "dev.zio" %% "zio-streams-compress-tar" % "@VERSION@"
)
```

Each archive format has a reference page: [Zip](./reference-zip.md), [Zip4J](./reference-zip4j.md) and [Tar](./reference-tar.md).

## Example

The example below compresses a file with Gzip, lists the entries of a Tar archive compressed with Gzip, and creates a ZIP archive:

```scala mdoc:compile-only
import zio._
import zio.compress._
import zio.stream._

object ExampleApp extends ZIOAppDefault {
  override def run: ZIO[Any, Any, Any] =
    for {
      // Compress a file with Gzip
      _ <- ZStream
             .fromFileName("file")
             .via(GzipCompressor.compress)
             .run(ZSink.fromFileName("file.gz"))

      // List all items in a gzipped tar archive
      _ <- ZStream
             .fromFileName("file.tgz")
             .via(GzipDecompressor.decompress)
             .via(TarUnarchiver().list)
             .mapZIO { entry =>
               Console.printLine(s"${entry.name} ${entry.uncompressedSize.getOrElse(-1L)}")
             }
             .runDrain

      // Create a ZIP archive with one entry
      _ <- ZStream(entry("file1.txt", "Hello world!".getBytes("UTF-8")))
             .via(ZipArchiver().archive)
             .run(ZSink.fromFileName("file.zip"))
    } yield ()

  private def entry(
    name: String,
    content: Array[Byte],
  ): (ArchiveEntry[Option, Any], ZStream[Any, Throwable, Byte]) =
    (ArchiveEntry[Option, Any](name), ZStream.fromIterable(content))
}
```

When you read an archive with `Unarchiver#unarchive`, read each entry's content completely before the next entry is emitted, or the pipeline locks up. [How Unarchiving Streams Entries](./streaming-unarchiving.md) explains why.

## Where to Go Next

- [Getting Started](./getting-started.md) adds a module and runs a first program.
- [Choosing a Codec or Archive Format](./choosing-a-codec.md) compares the formats.
- [How Unarchiving Streams Entries](./streaming-unarchiving.md) explains the one rule for reading archives.
- The reference pages describe every option and default, from [Core Interfaces](./reference-core.md) to [Tar](./reference-tar.md).
- [Tutorial: Compress and Archive Files](./tutorial-compress-and-archive.md) walks through the basics in order.
- The How-to Guides solve one task each, for example [How to Write a Password-Protected ZIP](./how-to-write-encrypted-zip.md).
