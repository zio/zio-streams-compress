[//]: # (This file was autogenerated using `zio-sbt-website` plugin via `sbt generateReadme` command.)
[//]: # (So please do not edit it manually. Instead, change "docs/index.md" file or sbt setting keys)
[//]: # (e.g. "readmeDocumentation" and "readmeSupport".)

# ZIO Streams Compress docs

[ZIO Streams Compress](https://github.com/zio/zio-streams-compress) integrates several compression algorithms and
archive formats with [ZIO Streams](https://zio.dev).

[![Concept](https://img.shields.io/badge/Project%20Stage-Concept-orange.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-streams-compress/workflows/CI/badge.svg) [![Sonatype Snapshots](https://img.shields.io/nexus/s/https/oss.sonatype.org/dev.zio/zio-streams-compress-docs_2.13.svg?label=Sonatype%20Snapshot)](https://oss.sonatype.org/content/repositories/snapshots/dev/zio/zio-streams-compress-docs_2.13/) [![ZIO Streams Compress docs](https://img.shields.io/github/stars/zio/zio-streams-compress?style=social)](https://github.com/zio/zio-streams-compress) [![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)

## Usage

In order to use this library, we need to add one of the following line in our `build.sbt` file:

```sbt
libraryDependencies += "dev.zio" %% "zio-streams-compress-gzip" % "<version>"
libraryDependencies += "dev.zio" %% "zio-streams-compress-zip" % "<version>"
libraryDependencies += "dev.zio" %% "zio-streams-compress-zip4j" % "<version>"
libraryDependencies += "dev.zio" %% "zio-streams-compress-tar" % "<version>"
libraryDependencies += "dev.zio" %% "zio-streams-compress-bzip2" % "<version>"
libraryDependencies += "dev.zio" %% "zio-streams-compress-zstd" % "<version>"
libraryDependencies += "dev.zio" %% "zio-streams-compress-brotli" % "<version>"
libraryDependencies += "dev.zio" %% "zio-streams-compress-lz4" % "<version>"
```

For ZIP files you can choose between the 'zip' and the 'zip4j' version. The first allows you to tweak the compression
level, while the second allows you work with password-protected ZIP files.

Currently only jvm is supported. PRs for scala-js and scala-native are welcome.

### Example

```scala
// Example.sc
// Run with: scala-cli Example.sc
//> using dep dev.zio:zio-streams-compress-gzip:0.0.1
//> using dep dev.zio:zio-streams-compress-zip:0.0.1
//> using dep dev.zio:zio-streams-compress-tar:0.0.1

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

## Documentation

Learn more on the [ZIO Streams Compress docs homepage](https://zio.dev/zio-streams-compress)!

## Contributing

For the general guidelines, see ZIO [contributor's guide](https://zio.dev/contributor-guidelines).

## Code of Conduct

See the [Code of Conduct](https://zio.dev/code-of-conduct)

## Support

Come chat with us on [![Badge-Discord]][Link-Discord].

[Badge-Discord]: https://img.shields.io/discord/629491597070827530?logo=discord "chat on discord"
[Link-Discord]: https://discord.gg/2ccFBr4 "Discord"

## Credits

This library is heavily inspired by [fs2-compress](https://github.com/lhns/fs2-compress).

## License

[License](LICENSE)

Copyright 2024-2024 Erik van Oosten and the zio-streams-compress contributors.
