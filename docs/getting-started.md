---
id: getting-started
title: "Getting Started with ZIO Streams Compress"
sidebar_label: "Getting Started"
---

This page adds one module to a project, compresses and decompresses a stream with it, and points to the pages to read next. It assumes a project that already uses ZIO 2 and ZIO Streams.

## Add a Module

Each compression format is its own module. We start with Gzip, because it needs no library beyond the JDK. Add it to your `build.sbt`:

```sbt
libraryDependencies += "dev.zio" %% "zio-streams-compress-gzip" % "@VERSION@"
```

The module pulls in `core` and ZIO Streams. For the full list of modules and the Scala versions they support, see [Installation](./index.md#installation) and [Modules](./index.md#modules).

## Compress and Decompress a Stream

Every codec is a `Compressor`, a `Decompressor`, or both, and each one exposes a `ZPipeline` from bytes to bytes. We attach it to a stream with `ZStream#via`. This program compresses a text, decompresses the result, and prints both sizes and the restored text:

```scala mdoc:compile-only
import zio._
import zio.compress._
import zio.stream._

object GzipRoundTrip extends ZIOAppDefault {

  private val text = "Hello, ZIO Streams Compress! " * 100

  override def run: ZIO[Any, Throwable, Unit] =
    for {
      compressed <- ZStream
                      .fromIterable(text.getBytes("UTF-8"))
                      .via(GzipCompressor.compress)
                      .runCollect
      restored <- ZStream
                    .fromChunk(compressed)
                    .via(GzipDecompressor.decompress)
                    .runCollect
      _ <- Console.printLine(s"original: ${text.length} bytes, compressed: ${compressed.size} bytes")
      _ <- Console.printLine(s"round trip intact: ${new String(restored.toArray, "UTF-8") == text}")
    } yield ()
}
```

`GzipCompressor.compress` and `GzipDecompressor.decompress` use the default options. To set options, such as the compression level, create an instance first and call `Compressor#compress` on it, as the [Gzip](./reference-gzip.md) reference shows.

The same code works with files. Replace `ZStream.fromIterable` with `ZStream.fromFileName("notes.txt")`, and `ZStream#runCollect` with `ZStream#run(ZSink.fromFileName("notes.txt.gz"))`.

## Switch to Another Codec

Because every codec has the same shape, switching formats means changing one value. Add the module of the new codec, for example `zio-streams-compress-zstd`, and replace the pipelines:

```scala mdoc:compile-only
import zio.compress._
import zio.stream._

val compressor: Compressor = ZstdCompressor()
val decompressor: Decompressor = ZstdDecompressor()

def roundTrip(bytes: ZStream[Any, Throwable, Byte]): ZStream[Any, Throwable, Byte] =
  bytes.via(compressor.compress).via(decompressor.decompress)
```

[Choosing a Codec or Archive Format](./choosing-a-codec.md) compares the codecs, so you can pick the one that fits your data.

## Read an Archive

Archive formats work the same way, but their pipelines carry named entries instead of plain bytes. With the `tar` module added, this program lists the entries of a `.tar.gz` file by first removing the Gzip layer and then reading the Tar archive:

```scala mdoc:compile-only
import zio._
import zio.compress._
import zio.stream._

val listTarGz: ZIO[Any, Throwable, Unit] =
  ZStream
    .fromFileName("archive.tar.gz")
    .via(GzipDecompressor.decompress)
    .via(TarUnarchiver().list)
    .mapZIO(entry => Console.printLine(s"${entry.name}: ${entry.uncompressedSize.value} bytes"))
    .runDrain
```

`Unarchiver#list` emits only the metadata of each entry. To read the content as well, use `Unarchiver#unarchive`, and read each entry's content before the next entry arrives; [How Unarchiving Streams Entries](./streaming-unarchiving.md) explains why.

## Next Steps

From here, pick the page that fits what you need:

- [Tutorial: Compress and Archive Files](./tutorial-compress-and-archive.md) builds a complete program step by step, with Gzip and ZIP.
- [How to Create and Read Archives](./how-to-create-and-read-archives.md) writes ZIP and Tar archives.
- [Core Interfaces](./reference-core.md) describes `Compressor`, `Decompressor`, `Archiver`, `Unarchiver` and `ArchiveEntry` in full.
