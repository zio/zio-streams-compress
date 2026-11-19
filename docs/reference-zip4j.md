---
id: reference-zip4j
title: "Zip4J"
sidebar_label: "Zip4J"
---

The `zip4j` module creates and reads ZIP archives with the [Zip4J](https://github.com/srikanth-lingala/zip4j) library. Use it when you need to read or write password-protected archives, with the ZIP standard encryption or with AES. It is also the better choice for stored (uncompressed) entries that are too large to hold in memory. It requires every entry to declare its uncompressed size when you write one. Use the [Zip](./reference-zip.md) module when you cannot provide the uncompressed size up front.

Add the module to your `build.sbt`:

```sbt
libraryDependencies += "dev.zio" %% "zio-streams-compress-zip4j" % "@VERSION@"
```

## Reading Archives

`Zip4JUnarchiver` is an `Unarchiver` that reads ZIP archives. Each entry comes with its content as a stream. Use `Zip4JUnarchiver.apply` with a password for a password-protected archive:

```scala mdoc:compile-only
import zio.compress._
import zio.stream._

val contents: ZStream[Any, Throwable, (String, Long)] =
  ZStream
    .fromFileName("secret.zip")
    .via(Zip4JUnarchiver(password = Some("it is a secret")).list)
    .map(entry => entry.name -> entry.uncompressedSize.getOrElse(-1L))
```

For an archive without a password, omit the `password` argument, or pass `None`:

```scala mdoc:compile-only
import zio.compress._
import zio.stream._

val unprotected = ZStream.fromFileName("archive.zip").via(Zip4JUnarchiver().unarchive)
```

`Zip4JUnarchiver#list` reads each entry's content to fill in the uncompressed size. `Zip4JUnarchiver#unarchive` gives the content as a stream, and it must be fully read before the next entry is emitted. See [Read Archives Safely](./how-to-read-archives-safely.md).

## Writing Archives

`Zip4JArchiver` is an `Archiver` that writes ZIP archives. Every entry must have a known uncompressed size, so the entry type is `ArchiveEntry[Some, _]`:

```scala mdoc:compile-only
import zio.compress._
import zio.stream._

val content = "Hello, ZIO!".getBytes("UTF-8")

val entry = ArchiveEntry[Option, Any]("notes/readme.txt").withKnownUncompressedSize(content.length.toLong)

val archive = ZStream((entry, ZStream.fromIterable(content))).via(Zip4JArchiver().archive)
```

If the number of bytes written does not match the declared size, the stream dies with an `IllegalStateException`.

## Writing Encrypted Archives

`Zip4JArchiver.encrypted` makes an archiver that encrypts every file entry with the given password and encryption method. Directory entries are not encrypted:

```scala mdoc:compile-only
import zio.compress._
import zio.compress.zip4j._
import zio.stream._

val content = "Hello, ZIO!".getBytes("UTF-8")

val entry = ArchiveEntry[Option, Any]("notes/readme.txt").withKnownUncompressedSize(content.length.toLong)

val archive = ZStream((entry, ZStream.fromIterable(content)))
                .via(Zip4JArchiver.encrypted("password", EncryptionMethod.AES_256).archive)
```

`EncryptionMethod` lives in the package `zio.compress.zip4j` and has three values, listed in `EncryptionMethod.Values`:

- `EncryptionMethod.ZipStandard` is the original ZIP encryption. Almost every tool reads it, but it is weak and easy to break.
- `EncryptionMethod.AES_128` is AES with a 128-bit key.
- `EncryptionMethod.AES_256` is AES with a 256-bit key, the strongest choice.

AES is not well supported by other tools, so some programs that read ZIP standard archives cannot open AES-encrypted entries. Check what your readers use before you choose AES.

`Zip4JArchiver(password = Some(...))` is a shortcut for `Zip4JArchiver.encrypted(password, EncryptionMethod.ZipStandard)`. [How to Write a Password-Protected ZIP](./how-to-write-encrypted-zip.md) shows a complete program.

:::warning[Standard encryption is very weak]
`Zip4JArchiver(password = Some(...))` uses the standard ZIP encryption, which is very weak. Use `Zip4JArchiver.encrypted(...)` with an AES method to protect sensitive data.
:::

:::warning[Encrypting with 1.x or 2.x]
Prior to version 3.0.0, `Zip4JArchiver(password = Some(...))` does not encrypt the entries. The archive it writes is identical to the archive written without a password. Do not rely on this option to protect data on those versions. Reading password-protected archives works in every version.
:::

## Options

The archiver and the unarchiver take these options:

| Option             | Type                                                | Default              |
|--------------------|-----------------------------------------------------|----------------------|
| `password`         | `Option[String]` (archiver, unarchiver)             | `None` (no password) |
| `encryptionMethod` | `EncryptionMethod` (`Zip4JArchiver.encrypted` only) | none, required       |
| `zipMethod`        | `ZipMethod` (archiver)                              | `Deflated`           |
| `chunkSize`        | `Int` (unarchiver)                                  | 64 KiB               |

`Zip4JUnarchiver` uses the password to decrypt encrypted entries, both ZIP standard and AES.

`ZipMethod` has two values: `ZipMethod.Deflated` compresses each entry, and `ZipMethod.Stored` writes each entry without compression. `ZipMethod.Values` lists both. This `ZipMethod` lives in the package `zio.compress.zip4j`, and it is a different type from the `ZipMethod` of the Zip module. Unlike the Zip module, Zip4J streams stored entries without holding them in memory:

```scala mdoc:compile-only
import zio.compress._
import zio.compress.zip4j._

val stored = Zip4JArchiver(zipMethod = ZipMethod.Stored)

val storedAndEncrypted = Zip4JArchiver.encrypted("password", EncryptionMethod.AES_128, ZipMethod.Stored)
```

## Entry Metadata

The entries that `Zip4JUnarchiver` returns include the name, the directory flag and, when the archive records it, the uncompressed size. `Zip4JArchiver` writes the name, the size, the directory flag and the `lastModified` time of each `ArchiveEntry`. It does not write `lastAccess` or `creation`. Set `isDirectory` to `true` to write a directory entry, and the name gets a trailing `/`.

The underlying entry differs by direction. On read, it is Zip4J's `LocalFileHeader`, for example to check `isEncrypted`. On write, the archiver accepts Zip4J's `ZipParameters`, for example to set a file comment, and then applies the name, size, compression and encryption settings on top. Both need `import zio.compress.Zip4J._`. [Core Interfaces](./reference-core.md#underlying-entries) describes underlying entries.

## Notes

- The unarchiver's error type is `IOException` for the entry content, and `Throwable` for the archive structure.
- Use [Zip](./reference-zip.md) for archives without a password. It accepts entries whose size is not known in advance.
