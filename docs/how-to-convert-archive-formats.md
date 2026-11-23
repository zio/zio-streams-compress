---
id: how-to-convert-archive-formats
title: "How to Convert a ZIP Archive to a Tar Archive"
sidebar_label: "Convert a ZIP Archive to Tar"
---

This guide converts a ZIP archive into a gzipped Tar archive in one streaming pass, without unpacking it to disk. The same steps convert in the other direction, or between any two archive formats of this library.

## The Problem

Converting by hand means opening both formats' streams, copying entry by entry, and holding each entry in memory to learn its size, because a Tar header needs the size before the content:

```scala mdoc:compile-only
import java.io.{FileInputStream, FileOutputStream}
import java.util.zip.{GZIPOutputStream, ZipInputStream}
import org.apache.commons.compress.archivers.tar.{TarArchiveEntry, TarArchiveOutputStream}

def convertByHand(input: String, output: String): Unit = {
  val zip = new ZipInputStream(new FileInputStream(input))
  val tar = new TarArchiveOutputStream(new GZIPOutputStream(new FileOutputStream(output)))
  try {
    Iterator.continually(zip.getNextEntry).takeWhile(_ != null).foreach { entry =>
      val bytes = zip.readAllBytes()
      val header = new TarArchiveEntry(entry.getName)
      header.setSize(bytes.length.toLong)
      tar.putArchiveEntry(header)
      tar.write(bytes)
      tar.closeArchiveEntry()
    }
  } finally {
    tar.close()
    zip.close()
  }
}
```

The library's pipelines avoid that bookkeeping and stream the content, but their entry types differ. `ZipUnarchiver#unarchive` emits `ArchiveEntry[Option, ZipEntry]`: the size of an entry may be missing, and the entry carries a JDK `ZipEntry` as its underlying value. `TarArchiver#archive` accepts only `ArchiveEntry[Some, _]`, and it expects either no underlying value or a `TarArchiveEntry`. Piping one into the other does not compile, and once the sizes are fixed, it fails at runtime on the foreign underlying value. Each entry needs two small changes on its way through.

## Add the Modules

The conversion uses three modules, one for each format and one for the Gzip layer:

```sbt
// zio-streams-compress-zip: ZIP archiver and unarchiver, built on the JDK
libraryDependencies += "dev.zio" %% "zio-streams-compress-zip" % "@VERSION@"

// zio-streams-compress-tar: Tar archiver and unarchiver, built on Apache Commons Compress
libraryDependencies += "dev.zio" %% "zio-streams-compress-tar" % "@VERSION@"

// zio-streams-compress-gzip: Gzip compressor and decompressor, built on the JDK
libraryDependencies += "dev.zio" %% "zio-streams-compress-gzip" % "@VERSION@"
```

## Convert One Entry

The function below turns an entry read from the ZIP archive into an entry the Tar archiver accepts. First, it removes the underlying `ZipEntry` with `ArchiveEntry#withUnderlying(())`. Without this, `TarArchiver` stops the stream with an `IllegalUnderlyingEntry` error (`Unsupported underlying entry of type java.util.zip.ZipEntry`), because it cannot copy a `ZipEntry` into a Tar header. The name, directory flag and timestamps stay on the `ArchiveEntry`, so they carry over. Second, it provides the size. When the ZIP entry records its size, the content streams straight through. When it does not, which is common for archives written by streaming tools, the function reads the content into memory to count it.

The function, with the imports the rest of the guide uses:

```scala mdoc:silent
import zio._
import zio.compress._
import zio.stream._

def toTarEntry(
  entry: ArchiveEntry[Option, Any],
  content: ZStream[Any, Throwable, Byte],
): Task[(ArchiveEntry[Some, Any], ZStream[Any, Throwable, Byte])] = {
  val plain = entry.withUnderlying(())
  plain.uncompressedSize match {
    case Some(size) =>
      ZIO.succeed(plain.withKnownUncompressedSize(size) -> content)
    case None =>
      content.runCollect.map(bytes => plain.withKnownUncompressedSize(bytes.size.toLong) -> ZStream.fromChunk(bytes))
  }
}
```

Reading the content inside `toTarEntry` also satisfies the rule that each entry's content is read before the next entry arrives. When the content streams through instead, `TarArchiver` reads it before it asks for the next entry, so the rule still holds. [How Unarchiving Streams Entries](./streaming-unarchiving.md) explains the rule.

## Stream the Whole Archive

With the entry function in place, the conversion is one pipeline: read the ZIP bytes, unarchive them, convert each entry, archive the entries as Tar, and compress the result with Gzip:

```scala mdoc:silent
def zipToTarGz(input: String, output: String): Task[Long] =
  ZStream
    .fromFileName(input)
    .via(ZipUnarchiver().unarchive)
    .mapZIO { case (entry, content) => toTarEntry(entry, content) }
    .via(TarArchiver().archive)
    .via(GzipCompressor.compress)
    .run(ZSink.fromFileName(output))
```

`zipToTarGz("photos.zip", "photos.tar.gz")` converts a file. Nothing is written to disk besides the output.

## Check the Result

To see the conversion work, this check builds a small ZIP archive in memory, converts it, and lists the entries of the Tar archive that comes out:

```scala mdoc:compile-only
val zipBytes: Task[Chunk[Byte]] =
  ZStream(
    ArchiveEntry[Option, Any]("docs", isDirectory = true) -> ZStream.empty,
    ArchiveEntry[Option, Any]("docs/notes.txt") -> ZStream.fromIterable("Meeting at 10:00".getBytes("UTF-8")),
  ).via(ZipArchiver().archive).runCollect

val tarListing: Task[Chunk[String]] =
  zipBytes.flatMap { bytes =>
    ZStream
      .fromChunk(bytes)
      .via(ZipUnarchiver().unarchive)
      .mapZIO { case (entry, content) => toTarEntry(entry, content) }
      .via(TarArchiver().archive)
      .via(TarUnarchiver().list)
      .map(entry => s"${entry.name} directory=${entry.isDirectory} size=${entry.uncompressedSize.value}")
      .runCollect
  }
```

`tarListing` yields `docs/ directory=true size=0` and `docs/notes.txt directory=false size=16`. The ZIP writer streamed `notes.txt` without recording its size, so this entry took the in-memory path of `toTarEntry`, while the directory, whose size of zero is recorded, streamed straight through.

## Notes

- The in-memory fallback holds one entry at a time. For ZIP archives with large entries of unknown size, write each entry's content to a temporary file instead, and stream it from there with its file size.
- `TarArchiver` checks each declared size against the bytes it receives. A ZIP entry whose recorded size is wrong stops the stream with an `IllegalStateException`.
- To convert in the other direction, from Tar to ZIP, no size is needed, because `ZipArchiver` accepts entries without one. Only the underlying value needs removing, as above.
- The [Zip](./reference-zip.md) and [Tar](./reference-tar.md) reference pages list the metadata each format keeps.
