---
id: how-to-write-encrypted-zip
title: "How to Write a Password-Protected ZIP"
sidebar_label: "Write a Password-Protected ZIP"
---

This guide writes a ZIP archive whose entries are encrypted with a password, streamed from content that is already on disk or in memory. It uses the Zip4J module, encrypts with AES-256, and then reads the archive back to check the password.

## The Problem

A ZIP archive often carries data that only its recipient may read: exported reports, backups, files for a customer. The `zip` module, built on the JDK, cannot encrypt. Zip4J can, but its own API writes to a `java.io.OutputStream` with mutable `ZipParameters` per entry, which does not fit a `ZStream` pipeline. `Zip4JArchiver` wraps that API as a `ZPipeline`, so encryption becomes one more step in the stream.

## Add the Module

Add the Zip4J module to your `build.sbt`:

```sbt
libraryDependencies += "dev.zio" %% "zio-streams-compress-zip4j" % "@VERSION@"
```

The encryption settings live in the package `zio.compress.zip4j`, next to the `zio.compress` types.

## Give Every Entry Its Size

`Zip4JArchiver` needs the uncompressed size of each entry before it writes the entry's content, so it only accepts `ArchiveEntry[Some, _]`. For a file on disk, take the size from the file system. This helper builds one entry per file:

```scala mdoc:silent
import zio._
import zio.compress._
import zio.compress.zip4j._
import zio.stream._

import java.nio.file.{Files, Paths}

def fileEntry(path: String): Task[(ArchiveEntry[Some, Any], ZStream[Any, Throwable, Byte])] =
  ZIO.attemptBlocking(Files.size(Paths.get(path))).map { size =>
    ArchiveEntry[Option, Any](path).withKnownUncompressedSize(size) -> ZStream.fromFileName(path)
  }
```

If the bytes streamed for an entry do not match its declared size, the archiver stops the stream with an `IllegalStateException`. Content created in memory can use the length of its byte array instead.

## Encrypt the Archive

`Zip4JArchiver.encrypted` takes the password and the encryption method. AES-256 is the strongest of the three methods that `EncryptionMethod` offers. This program writes two files into `reports.zip`:

```scala mdoc:compile-only
def writeEncrypted(password: String, files: Seq[String], output: String): Task[Long] =
  ZStream
    .fromIterable(files)
    .mapZIO(fileEntry)
    .via(Zip4JArchiver.encrypted(password, EncryptionMethod.AES_256).archive)
    .run(ZSink.fromFileName(output))

val writeReports: Task[Long] =
  System
    .env("REPORTS_PASSWORD")
    .someOrFail(new IllegalStateException("REPORTS_PASSWORD is not set"))
    .flatMap(password => writeEncrypted(password, Seq("january.csv", "february.csv"), "reports.zip"))
```

The password comes from an environment variable, so it stays out of the source code. Every file entry is encrypted; directory entries, which have no content, are not.

To change the compression as well, pass a third argument: `Zip4JArchiver.encrypted(password, EncryptionMethod.AES_256, ZipMethod.Stored)` stores the encrypted entries without compressing them, which suits data that is already compressed, such as images.

## Choose the Encryption Method

The right method depends on who opens the archive:

- `EncryptionMethod.AES_256` or `EncryptionMethod.AES_128` protects the data well. Some tools that read ZIP files cannot open AES-encrypted entries, so check that the recipients use one that can, such as 7-Zip.
- `EncryptionMethod.ZipStandard` opens almost everywhere, including with `unzip`, but it is weak and easy to break. `Zip4JArchiver(password = Some(password))` is a shortcut for it. Use it only to keep casual readers out.

Archives written by zio-streams-compress before 3.0.0 with `Zip4JArchiver(password = Some(...))` are not encrypted at all. Upgrade before relying on the password.

## Check the Password

To check the result, read the archive back with `Zip4JUnarchiver` and the same password. `Unarchiver#list` decrypts every entry to count its bytes, so it fails when the password is wrong. This check builds a small encrypted archive in memory and lists it with a given password:

```scala mdoc:compile-only
val report = "id,total\n1,42\n".getBytes("UTF-8")

val encryptedReport: Task[Chunk[Byte]] =
  ZStream(ArchiveEntry[Option, Any]("report.csv").withKnownUncompressedSize(report.length.toLong) -> ZStream.fromIterable(report))
    .via(Zip4JArchiver.encrypted("s3cret", EncryptionMethod.AES_256).archive)
    .runCollect

def listWith(password: String): Task[Chunk[String]] =
  encryptedReport.flatMap { bytes =>
    ZStream
      .fromChunk(bytes)
      .via(Zip4JUnarchiver(password = Some(password)).list)
      .map(entry => s"${entry.name}: ${entry.uncompressedSize.value} bytes")
      .runCollect
  }
```

`listWith("s3cret")` succeeds with `Chunk("report.csv: 14 bytes")`. `listWith("guess")` fails with Zip4J's `ZipException`, whose message reports a wrong password.

`Unarchiver#list` emits `ArchiveEntry[Some, _]`, so `uncompressedSize.value` is always present. [How to Read a Password-Protected ZIP](./how-to-read-password-protected-zip.md) shows how to read the content as well.

## Notes

- The [Zip4J](./reference-zip4j.md) reference lists every option of `Zip4JArchiver` and `Zip4JUnarchiver`.
- `Zip4JArchiver` writes the name, size and `lastModified` time of each entry, but not `lastAccess` or `creation`.
- An archive without a password whose entry sizes are not known in advance is easier to write with the [Zip](./reference-zip.md) module.
