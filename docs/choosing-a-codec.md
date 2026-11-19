---
id: choosing-a-codec
title: "Choosing a Codec or Archive Format"
sidebar_label: "Choosing a Codec"
---

ZIO Streams Compress ships ten modules, and most of them do the same job: turn bytes into fewer bytes and back. This page explains how they differ, so you can pick one before you read its reference page.

## Codec or Archive Format

The first choice is between a codec and an archive format, because they answer different needs:

- A **codec** compresses one stream of bytes. It has no file names and no entries. Gzip, Zstd, Brotli, Bzip2, LZ4 and Snappy are codecs. Each one is a `Compressor`, a `Decompressor`, or both.
- An **archive format** bundles many named entries (files and directories) into one stream of bytes. ZIP and Tar are archive formats. Each one is an `Archiver`, an `Unarchiver`, or both.

The two combine. A Tar archive stores its entries uncompressed, so a `.tar.gz` file is a Tar archive piped through Gzip, and a `.tar.zst` file is the same archive piped through Zstd. A ZIP archive already compresses each entry, so it is rarely wrapped in a codec. The [Core Interfaces](./reference-core.md) page describes the four interfaces.

## Compare the Codecs

Each codec makes its own trade-off between speed, compression ratio, memory and portability:

| Format | Module     | Compress | Decompress | Trade-off                                                   |
|--------|------------|----------|------------|-------------------------------------------------------------|
| Gzip   | `gzip`     | Yes      | Yes        | Readable everywhere. Built on the JDK, no extra library.    |
| Zstd   | `zstd`     | Yes      | Yes        | Good ratio at high speed. Native library. Parallel workers. |
| Brotli | `brotli`   | No       | Yes        | Pure Java, decompression only.                              |
| Brotli | `brotli4j` | Yes      | Yes        | Best ratio on text at high quality. Native library.         |
| Bzip2  | `bzip2`    | Yes      | Yes        | Good ratio, slow, and needs a lot of memory.                |
| LZ4    | `lz4`      | Yes      | Yes        | Very fast, lower ratio.                                     |
| Snappy | `snappy`   | Yes      | Yes        | Very fast, lower ratio. Three incompatible formats.         |

Some rules of thumb follow from the table:

- Choose Gzip when other programs must read the data and you do not know which tools they have. Every platform reads Gzip.
- Choose Zstd for a better ratio than Gzip at a similar or higher speed, when both sides use this library or another Zstandard reader. Its `workers` option compresses on several threads.
- Choose LZ4 or Snappy when speed matters more than size, for example for data that is written once and read soon after. Snappy needs care: the reader must use the same format variant as the writer, see [Snappy](./reference-snappy.md).
- Choose Brotli for text served over HTTP, where browsers already read it. To write Brotli, add `brotli4j`. To only read it without a native library, add `brotli`.
- Choose Bzip2 when you must read or write `.bz2` files. It is rarely the best choice for new data.

Three modules load a native library at runtime: `zstd`, `brotli4j` and `snappy`. The zstd-jni and snappy-java jars bundle builds for the common platforms. Brotli4J instead pulls a separate native artifact for the platform that resolves the dependencies, see [Brotli4J](./reference-brotli4j.md#native-library). On an unusual platform or a locked-down container, a pure-Java codec (Gzip, or Brotli for reading) avoids native libraries altogether.

## Compare the Archive Formats

The three archive modules differ in what they need to know about each entry before they write it:

| Format | Module  | Size needed on write | Password protection                | Built on                |
|--------|---------|----------------------|------------------------------------|-------------------------|
| ZIP    | `zip`   | No                   | No                                 | JDK `java.util.zip`     |
| ZIP    | `zip4j` | Yes                  | Read and write (ZIP standard, AES) | Zip4J                   |
| Tar    | `tar`   | Yes                  | No                                 | Apache Commons Compress |

Choose by what you have and who reads the archive:

- Choose `zip` when the size of each entry is not known before its content is streamed, for example content produced on the fly. It is the default choice for ZIP.
- Choose `zip4j` when the archive must be password protected, or when it must store entries without compression and the entries are too large to hold in memory. The `zip` module buffers each stored entry in memory to compute its checksum. Zip4J does not.
- Choose `tar` for Unix tooling, usually wrapped in Gzip or Zstd. Through its underlying `TarArchiveEntry`, Tar also keeps Unix metadata such as permissions and the owner.

The size requirement shows up in the types. `ZipArchiver` accepts `ArchiveEntry[Option, _]`, while `Zip4JArchiver` and `TarArchiver` accept only `ArchiveEntry[Some, _]`, so a missing size is a compile error rather than a runtime failure. [Core Interfaces](./reference-core.md#size-requirements-on-write) explains the `Size` type parameter.

## Switching Later

Because every codec has the same `Compressor` and `Decompressor` shape, a program can keep the choice in one value and change it later without touching the rest of the code. [How to Compress and Decompress a Stream](./how-to-compress-and-decompress.md) shows that pattern. Archives are less interchangeable, because of the size requirement, but [How to Convert a ZIP Archive to a Tar Archive](./how-to-convert-archive-formats.md) shows how to move entries from one format to the other.
