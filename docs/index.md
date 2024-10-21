---
id: index 
title: "Compression and archives with zio-streams"
sidebar_label: "Getting Started"
---

[ZIO Streams Compress](https://github.com/zio/zio-streams-compress) integrates several compression algorithms and
archive formats with [ZIO Streams](https://zio.dev).

@PROJECT_BADGES@ [![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)

## Usage

In order to use this library, we need to add one of the following line in our `build.sbt` file:

```sbt
libraryDependencies += "dev.zio" %% "zio-streams-compress-gzip" % "@VERSION@"
libraryDependencies += "dev.zio" %% "zio-streams-compress-zip" % "@VERSION@"
libraryDependencies += "dev.zio" %% "zio-streams-compress-zip4j" % "@VERSION@"
libraryDependencies += "dev.zio" %% "zio-streams-compress-tar" % "@VERSION@"
libraryDependencies += "dev.zio" %% "zio-streams-compress-bzip2" % "@VERSION@"
libraryDependencies += "dev.zio" %% "zio-streams-compress-zstd" % "@VERSION@"
libraryDependencies += "dev.zio" %% "zio-streams-compress-brotli" % "@VERSION@"
libraryDependencies += "dev.zio" %% "zio-streams-compress-lz4" % "@VERSION@"
```

For ZIP files you can choose between the 'zip' and the 'zip4j' version. The first allows you to tweak the compression
level, while the second allows you work with password-protected ZIP files.

Currently only jvm is supported. PRs for scala-js and scala-native are welcome.

### Example

```scala
import zio._
import zio.compress.{ArchiveEntry, GzipCompressor, GzipDecompressor, TarUnarchiver, ZipArchiver}
import zio.stream._

import java.nio.charset.StandardCharsets.UTF_8

object ExampleApp extends ZIOAppDefault {
  override def run =
    for {
      // Compress a file with GZIP
      _ <- ZStream
        .fromFileName("file")
        .via(GzipCompressor.make().compress)
        .run(ZSink.fromFileName("file.gz"))

      // List all items in a gzip tar archive:
      _ <- ZStream
        .fromFileName("file.tgz")
        .via(GzipDecompressor.make().decompress)
        .via(TarUnarchiver.make().unarchive)
        .mapZIO { case (archiveEntry, contentStream) =>
          for {
            content <- contentStream.runCollect
            _ <- Console.printLine(s"${archiveEntry.name} ${content.length}")
          } yield ()
        }
        .runDrain

      // Create a ZIP archive (use the zip4j version for password support)
      _ <- ZStream(archiveEntry("file1.txt", "Hello world!".getBytes(UTF_8)))
        .via(ZipArchiver.make().archive)
        .run(ZSink.fromFileName("file.zip"))
    } yield ()

  private def archiveEntry(
      name: String,
      content: Array[Byte]
  ): (ArchiveEntry[Some, Any], ZStream[Any, Throwable, Byte]) = {
    (ArchiveEntry(name, Some(content.length)), ZStream.fromIterable(content))
  }
}
```

## Running the tests

```shell
SBT_OPTS="-Xmx4G -XX:+UseG1GC" sbt test
```