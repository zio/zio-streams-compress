---
id: how-to-read-archives-safely
title: "How to Read Archives Safely"
sidebar_label: "Read Archives Safely"
---

Reading an archive means reading entries that someone else may have written. This guide shows how to read entry contents
without losing data, and how to stop an entry that is too large before it fills the memory.

## Read Each Entry Before the Next One

Every unarchiver in this library emits entries in a streaming fashion. The content of an entry is a stream that reads
from the archive directly. When you move on to the next entry without reading the current content, the pipeline waits
for you and the program stalls.

For that reason, read each entry's content completely before the next entry is emitted. The simplest way is to run the
content stream inside the `mapZIO` that handles the entry:

```scala mdoc:compile-only
import zio._
import zio.compress._
import zio.stream._

val printSizes =
  ZStream
    .fromFileName("archive.zip")
    .via(ZipUnarchiver().unarchive)
    .mapZIO { case (archiveEntry, contentStream) =>
      contentStream.runCount.flatMap { count =>
        Console.printLine(s"${archiveEntry.name} has $count bytes")
      }
    }
    .runDrain
```

`runCount` reads the whole entry and counts its bytes, so the next entry is only emitted after this one is complete.

If you only need the metadata of each entry, use `list` instead. `list` reads the content for you, and it fills in the
uncompressed size. The same rule applies to all unarchivers.

## Stop Entries That Are Too Large

An archive entry can claim any uncompressed size, and the claim can be wrong. Read at most a fixed number of bytes from each
entry to protect the memory of your program. The size reported in the entry header is a hint, so check it and also
count the bytes you actually read:

```scala mdoc:compile-only
import zio._
import zio.compress._
import zio.stream._

val maxEntrySize: Long = 10L * 1024 * 1024

val loadSmallEntries =
  ZStream
    .fromFileName("archive.zip")
    .via(ZipUnarchiver().unarchive)
    .mapZIO { case (archiveEntry, contentStream) =>
      if (archiveEntry.uncompressedSize.exists(_ > maxEntrySize))
        ZIO.fail(new IllegalStateException(s"archive entry ${archiveEntry.name} is too large"))
      else
        contentStream.take(maxEntrySize + 1).runCollect.flatMap { content =>
          if (content.size > maxEntrySize)
            ZIO.fail(new IllegalStateException(s"archive entry ${archiveEntry.name} is too large"))
          else
            ZIO.succeed((archiveEntry.name, content))
        }
    }
    .rechunk(10)
```

The first check rejects entries that declare a large size. The second check handles entries that declare nothing, or
declare a smaller size than their content. `take(maxEntrySize + 1)` reads one byte past the limit, which is enough to
detect an overflow without reading the whole entry.

The `rechunk` after the check groups the entries into batches of ten, so later stages can process several at once.
Rechunking is safe here because each entry's content has already been read in full.

## Choose Between List and Unarchive

Use `list` when you need the name and the size of each entry, and nothing else. Use `unarchive` when you need the content.
The table shows the difference:

| Method      | Emits                                   | Reads content | Uncompressed size       |
|-------------|-----------------------------------------|---------------|-------------------------|
| `list`      | `ArchiveEntry`                          | Yes, to count | Always known            |
| `unarchive` | `ArchiveEntry` and its content stream   | No            | Only when the archive says so |

## Notes

- The uncompressed size from the archive header may be missing, wrong, or malicious. Count the bytes you read.
- A failure in one entry's content stream fails the whole archive stream. Handle the error in `mapZIO` if you want to skip
  the entry instead.
- For writing archives, see [Create and Read Archives](./how-to-create-and-read-archives.md).
