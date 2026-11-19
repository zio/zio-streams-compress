---
id: streaming-unarchiving
title: "How Unarchiving Streams Entries"
sidebar_label: "Streaming Unarchiving"
---

Every unarchiver in ZIO Streams Compress (`ZipUnarchiver`, `Zip4JUnarchiver` and `TarUnarchiver`) reads an archive as a stream, without loading it into memory or onto disk. That design comes with one rule: the content of each entry must be read completely before the next entry is emitted. This page explains where the rule comes from and what it means for your pipelines. For the code that follows the rule, see [How to Read Archives Safely](./how-to-read-archives-safely.md).

## One Byte Stream, Many Entries

An archive is a single sequence of bytes. The entries sit one after another: a header for the first entry, its content, a header for the second entry, its content, and so on:

```
archive bytes:  [header 1][content 1][header 2][content 2][header 3][content 3]...
                    |          |
unarchive emits: (entry 1, stream 1) -> (entry 2, stream 2) -> ...
```

`Unarchiver#unarchive` emits one pair per entry: the `ArchiveEntry` built from the header, and a `ZStream` of the content. Both content streams read from the same underlying byte stream. The content of entry 2 starts only where the content of entry 1 ends, so the unarchiver cannot read header 2 until something has read all of content 1.

## Why the Pipeline Waits

The unarchiver does not skip or buffer content on its own. When the downstream asks for the next entry, the unarchiver first waits until the content stream of the current entry has been read to its end. Only then does it read the next header and emit the next pair.

If nothing reads the current content stream, that wait never ends. The pipeline locks up and the program halts without an error. Reading only part of the content, for example with `ZStream#take`, has the same effect, because the end of the content is never reached.

Operators that pull the next element before they emit the current one trigger this by accident. `ZStream#rechunk` with a size above one, `ZStream#grouped` and `ZStream#zip` all ask for the second entry while the first content stream is still unread. Put such operators after the step that reads each entry's content, never directly after `Unarchiver#unarchive`.

## Why It Works This Way

ZIO Streams Compress 1.x had no such rule. Its unarchivers let the stream move on to the next entry at any time. Moving on, even by accident through buffering or rechunking, silently corrupted the content stream of every entry, because the bytes the next entry consumed were the unread bytes of the previous one.

Version 2.0 replaced the silent corruption with the wait. The pipeline now either produces correct content or stops, and it still streams: no entry is held in memory or copied to disk. The price is that each entry must be read before the next one starts.

## What It Means in Practice

Most pipelines follow the rule without effort. A step that handles an entry usually reads its content anyway, and the archivers of this library read the content stream of every entry before they ask for the next one, so an `Unarchiver#unarchive` pipeline can feed an archiver directly.

The rule holds for directory entries too. A directory has no content, but its content stream still has to be read to its end, because that is what tells the unarchiver to move on. A step that skips directories must still drain their content, for example with `content.runDrain`.

Two cases need care:

- When you only need the metadata of each entry, use `Unarchiver#list` instead of `Unarchiver#unarchive`. It reads every content stream for you, counts the bytes, and emits entries with a known `uncompressedSize`.
- When you need buffering, aggregation, rechunking or parallel processing for throughput, read each entry's content inside the step that handles it (for example in `ZStream#mapZIO` or `ZStream#mapZIOPar`), and apply the other operators after that step.

[How to Read Archives Safely](./how-to-read-archives-safely.md) shows both cases, and how to cap the size of each entry while reading it.
