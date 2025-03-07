---
id: index
title: "Compression and archives with zio-streams"
sidebar_label: "ZIO Streams Compress"
---

[ZIO Streams Compress](https://github.com/zio/zio-streams-compress) integrates several compression algorithms and
archive formats with [ZIO Streams](https://zio.dev).

@PROJECT_BADGES@ [![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)

## Installation

In order to use this library, we need to add one of the following line in our `build.sbt` file:

```sbt
libraryDependencies += "dev.zio" %% "zio-streams-compress-brotli" % "@VERSION@"
libraryDependencies += "dev.zio" %% "zio-streams-compress-brotli4j" % "@VERSION@"
libraryDependencies += "dev.zio" %% "zio-streams-compress-bzip2" % "@VERSION@"
libraryDependencies += "dev.zio" %% "zio-streams-compress-gzip" % "@VERSION@"
libraryDependencies += "dev.zio" %% "zio-streams-compress-lz4" % "@VERSION@"
libraryDependencies += "dev.zio" %% "zio-streams-compress-snappy" % "@VERSION@"
libraryDependencies += "dev.zio" %% "zio-streams-compress-tar" % "@VERSION@"
libraryDependencies += "dev.zio" %% "zio-streams-compress-zip" % "@VERSION@"
libraryDependencies += "dev.zio" %% "zio-streams-compress-zip4j" % "@VERSION@"
libraryDependencies += "dev.zio" %% "zio-streams-compress-zstd" % "@VERSION@"
```

For Brotli you can choose between the 'brotli' and the 'brotli4j' version. The first is based on the official Java
library but only does decompression. The second is based on [Brotli4J](https://github.com/hyperxpro/Brotli4j) which does
compression and decompression.

For ZIP files you can choose between the 'zip' and the 'zip4j' version. The first allows you to tweak the compression
level, while the second allows you work with password-protected ZIP files.

Currently only jvm is supported. PRs for scala-js and scala-native are welcome.

### Example

```scala
// Example.sc
// Run with: scala-cli Example.sc
//> using dep dev.zio:zio-streams-compress-gzip:@VERSION@
//> using dep dev.zio:zio-streams-compress-tar:@VERSION@
//> using dep dev.zio:zio-streams-compress-zip4j:@VERSION@

import zio._
import zio.compress.{ArchiveEntry, GzipCompressor, GzipDecompressor, TarUnarchiver, Zip4JArchiver}
import zio.stream._

import java.nio.charset.StandardCharsets.UTF_8

object ExampleApp extends ZIOAppDefault {
  override def run: ZIO[Any, Any, Any] =
    for {
      // Compress a file with GZIP
      _ <- ZStream
             .fromFileName("file")
             .via(GzipCompressor.compress)
             .run(ZSink.fromFileName("file.gz"))

      // List all items in a gzip tar archive:
      _ <- ZStream
             .fromFileName("file.tgz")
             .via(GzipDecompressor.decompress)
             .via(TarUnarchiver.unarchive)
             .mapZIO { case (archiveEntry, contentStream) =>
               for {
                 content <- contentStream.runCollect
                 _ <- Console.printLine(s"${archiveEntry.name} ${content.length}")
               } yield ()
             }
             .runDrain

      // Create an encrypted ZIP archive
      _ <- ZStream(archiveEntry("file1.txt", "Hello world!".getBytes(UTF_8)))
             .via(Zip4JArchiver(password = Some("it is a secret")).archive)
             .run(ZSink.fromFileName("file.zip"))
    } yield ()

  private def archiveEntry(
    name: String,
    content: Array[Byte],
  ): (ArchiveEntry[Some, Any], ZStream[Any, Throwable, Byte]) =
    (ArchiveEntry(name, Some(content.length.toLong)), ZStream.fromIterable(content))

}
```

## Running the tests

```shell
SBT_OPTS="-Xmx4G -XX:+UseG1GC" sbt test
```
