package zio.compress

import zio._
import zio.stream.ZStream
import zio.test._

import java.io.{InputStream, OutputStream}

object JavaIoInteropSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("JavaIoInterop")(
      test("viaInputStreamByte pipes bytes via an InputStream") {
        for {
          random <- Random.nextBytes(5000)
          out <- ZStream.fromChunk(random)
                   .rechunk(40) // not all data at once!
                   .via(JavaIoInterop.viaInputStreamByte(55, 2)(identity))
                   .runCollect
        } yield
          assertTrue(out == random)
      },
      test("viaInputStreamByte properly cleans up when the inputStream doesn't read to the end") {
        for {
          random <- Random.nextBytes(5000) // more than 2 chunks of 55 bytes
          out <- ZStream.fromChunk(random)
                   .rechunk(40) // not all data at once!
                   .via(JavaIoInterop.viaInputStreamByte(55, 2)(in => new LimitedInputStream(in, 10)))
                   .runCollect
        } yield
          assertTrue(out == random.take(10))
      },
      test("viaInputStreamByte properly cleans up when it isn't read to the end") {
        for {
          random <- Random.nextBytes(5000) // more than 2 chunks of 55 bytes
          out <- ZStream.fromChunk(random)
                   .rechunk(40) // not all data at once!
                   .via(JavaIoInterop.viaInputStreamByte(55, 2)(identity))
                   .take(10)
                   .runCollect
        } yield
          assertTrue(out == random.take(10))
      },
      test("viaOutputStreamByte pipes bytes via an OutputStream") {
        for {
          random <- Random.nextBytes(5000)
          out <- ZStream.fromChunk(random)
                   .rechunk(40) // not all data at once!
                   .via(JavaIoInterop.viaOutputStreamByte(identity, 55, 2))
                   .runCollect
        } yield
          assertTrue(out == random)
      },
      test("viaOutputStreamByte properly cleans up when the outputStream doesn't read to the end") {
        for {
          random <- Random.nextBytes(5000) // more than 2 chunks of 55 bytes
          out <- ZStream.fromChunk(random)
                   .rechunk(40) // not all data at once!
                   .via(JavaIoInterop.viaOutputStreamByte(out => new LimitedOutputStream(out, 10), 55, 2))
                   .runCollect
        } yield
          assertTrue(out == random.take(10))
      },
      test("viaOutputStreamByte properly cleans up when it isn't read to the end") {
        for {
          random <- Random.nextBytes(5000) // more than 2 chunks of 55 bytes
          out <- ZStream.fromChunk(random)
                   .rechunk(40) // not all data at once!
                   .via(JavaIoInterop.viaOutputStreamByte(identity, 55, 2))
                   .take(10)
                   .runCollect
        } yield
          assertTrue(out == random.take(10))
      },
    )

}

/** Only reads the first `limit` bytes from the wrapped inputStream. */
private class LimitedInputStream(wrapped: InputStream, limit: Int) extends InputStream {
  private[this] var toRead = limit

  override def read(): Int =
    if (toRead <= 0) {
      -1
    } else {
      toRead -= 1
      wrapped.read()
    }

  override def close(): Unit = wrapped.close()
}

/** Only writes the first `limit` bytes to the wrapped inputStream. */
private class LimitedOutputStream(wrapped: OutputStream, limit: Int) extends OutputStream {
  private[this] var toWrite = limit

  override def write(b: Int): Unit =
    if (toWrite > 0) {
      toWrite -= 1
      wrapped.write(b)
    }

  override def close(): Unit = wrapped.close()
}
