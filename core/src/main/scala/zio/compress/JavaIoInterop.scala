package zio.compress

import zio._
import zio.stream._

import java.io.{BufferedOutputStream, InputStream, OutputStream}

private[compress] object JavaIoInterop {

  /**
   * Makes a pipeline that makes the incoming ZStream available for reading via an InputStream. This InputStream can
   * then be wrapped by `makeInputStream`. All bytes read from the wrapped InputStream are put in the outgoing ZStream.
   *
   * This illustrates how data flows in the pipeline implementation:
   * {{{
   * Incoming ZStream --> queue <-- InputStream <-- wrapped InputStream <-- outgoing ZStream
   *
   * --> push
   * <-- pull
   * }}}
   *
   * This implementation always tries to read `chunkSize` bytes from the wrapped InputStream. However, when fewer bytes
   * are available, the chunks might be smaller than `chunkSize`. If the wrapped InputStream has a good `available()`
   * implementation, consider wrapping the wrapped InputStream with a `BufferedInputStream`. If the chunks are still too
   * small, then consider re-chunking the outgoing ZStream, e.g. with `ZStream.rechunk`.
   *
   * @param chunkSize
   *   The maximum chunk size of the outgoing ZStream. Defaults to `ZStream.DefaultChunkSize` (4KiB).
   * @param queueSize
   *   Chunks of the incoming ZStream go through an internal queue. This parameter determines the size of that queue.
   *   Defaults to 2.
   * @param makeInputStream
   *   Create the wrapped InputStream.
   * @return
   *   The created pipeline.
   */
  def viaInputStreamByte(
    chunkSize: Int = ZStream.DefaultChunkSize,
    queueSize: Int = Defaults.DefaultChunkedQueueSize,
  )(makeInputStream: InputStream => InputStream): ZPipeline[Any, Throwable, Byte, Byte] =
    viaInputStream[Byte](queueSize) { inputStream =>
      ZIO.attemptBlocking(ZStream.fromInputStream(makeInputStream(inputStream), chunkSize))
    }

  /**
   * Makes a pipeline that makes the incoming ZStream available for reading via an InputStream. This is then used by
   * function `streamReader` to produce the outgoing ZStream.
   *
   * This illustrates how data flows in the pipeline implementation:
   * {{{
   * Incoming ZStream --> queue <-- InputStream <-- wrapped InputStream <--streamReader-- outgoing ZStream
   *
   * --> push
   * <-- pull
   * }}}
   *
   * @param queueSize
   *   Chunks of the incoming ZStream go to an internal queue. This parameter determines the size of that queue.
   *   Defaults to 2.
   * @param streamReader
   *   A ZIO that reads from the given incoming InputStream to produce the outgoing ZStream. The outgoing ZStream must
   *   end when the InputStream closes.
   * @return
   *   The created pipeline.
   */
  def viaInputStream[Out](
    queueSize: Int = Defaults.DefaultChunkedQueueSize
  )(
    streamReader: InputStream => ZIO[Scope, Throwable, ZStream[Any, Throwable, Out]]
  ): ZPipeline[Any, Throwable, Byte, Out] =
    ZPipeline.fromFunction[Any, Throwable, Byte, Out] { stream =>
      ZStream.unwrapScoped {
        for {
          queue <- ZIO.acquireRelease(Queue.bounded[Take[Throwable, Byte]](queueSize))(_.shutdown)
          _ <- stream
                 .chunks
                 .map(Take.chunk)
                 .run(ZSink.fromQueue(queue))
                 .onDoneCause(
                   (cause: Cause[Throwable]) => queue.offer(Take.failCause(cause)),
                   _ => queue.offer(Take.end),
                 )
                 .forkScoped
          queueInputStream <- ZStream.fromQueue(queue).flattenTake.toInputStream
          result <- streamReader(queueInputStream)
        } yield result
      }
    }

  /**
   * Makes a pipeline that captures the output of an OutputStream and makes it available as the outgoing ZStream. The
   * OutputStream can then be wrapped (via `makeOutputStream`). The incoming ZStream of bytes is written to the wrapped
   * OutputStream.
   *
   * This illustrates how data flows in the pipeline implementation:
   * {{{
   * Incoming ZStream --> wrapping OutputStream --> buffered OutputStream --> queue <-- outgoing ZStream
   *
   * --> push
   * <-- pull
   * }}}
   *
   * Often, the wrapping OutputStreams needs to write to its underlying outputStream immediately (from the constructor).
   * Usually this is just a few bytes, for example a magic header indicating file type. Since the internal queue is
   * bounded (see parameter `queueSize`), and the queue reader starts later, there is limited space for this data. To be
   * precise, `queue-size * chunk-size` bytes (defaults to 2 * 64KiB = 128KiB) are available.
   *
   * The wrapped OutputStream is closed when the incoming ZStream ends.
   *
   * @param makeOutputStream
   *   A function that creates the wrapped OutputStream from an OutputStream that writes to the internal queue.
   * @param chunkSize
   *   The chunk size of the outgoing ZStream (also the size of the buffer in the buffered OutputStream). Defaults to
   *   64KiB.
   * @param queueSize
   *   The internal queue size. Defaults to 2.
   * @return
   *   The created pipeline.
   */
  def viaOutputStreamByte(
    makeOutputStream: OutputStream => OutputStream,
    chunkSize: Int = Defaults.DefaultChunkSize,
    queueSize: Int = Defaults.DefaultChunkedQueueSize,
  ): ZPipeline[Any, Throwable, Byte, Byte] =
    viaOutputStream[Byte, OutputStream](makeOutputStream, chunkSize, queueSize) { case (stream, outputStream) =>
      stream.runForeachChunk(chunk => ZIO.attemptBlocking(outputStream.write(chunk.toArray)))
    }

  /**
   * Makes a pipeline that captures the output of an OutputStream and makes it available as the outgoing ZStream. The
   * OutputStream can then be wrapped (via `makeOutputStream`). The input ZStream (with items of type `In`) is written
   * to the wrapped OutputStream by `streamWriter`.
   *
   * This illustrates how data flows in the pipeline implementation:
   * {{{
   * Incoming ZStream --streamWriter--> wrapping OutputStream --> buffered OutputStream --> queue --> outgoing ZStream
   * }}}
   *
   * Often, the wrapping OutputStreams needs to write to its underlying outputStream immediately (from the constructor).
   * Usually this is just a few bytes, for example a magic header indicating file type. Since the internal queue is
   * bounded (see parameter `queueSize`), and the queue reader starts later, there is limited space for this data. To be
   * precise, `queue-size * chunk-size` bytes (defaults to 2 * 64KiB = 128KiB) are available.
   *
   * The wrapped OutputStream is closed when the incoming ZStream ends.
   *
   * @param makeOutputStream
   *   A function that creates the wrapped OutputStream from an OutputStream that writes to the internal queue.
   * @param chunkSize
   *   The chunk size of the outgoing ZStream (also the size of the buffer in the buffered OutputStream). Defaults to
   *   64KiB.
   * @param queueSize
   *   The internal queue size. Defaults to 2.
   * @param streamWriter
   *   Function that writes items from the incoming ZStream to the wrapped OutputStream. The wrapped OutputStream should
   *   _not_ be closed when the incoming ZStream ends.
   * @tparam In
   *   Type of incoming items.
   * @tparam OS
   *   Type of the wrapping OutputStream.
   * @return
   *   The created pipeline.
   */
  def viaOutputStream[In, OS <: OutputStream](
    makeOutputStream: OutputStream => OS,
    chunkSize: Int = Defaults.DefaultChunkSize,
    queueSize: Int = Defaults.DefaultChunkedQueueSize,
  )(
    streamWriter: (ZStream[Any, Throwable, In], OS) => ZIO[Any, Throwable, Unit]
  ): ZPipeline[Any, Throwable, In, Byte] =
    ZPipeline.fromFunction[Any, Throwable, In, Byte] { stream =>
      ZStream.unwrapScoped {
        for {
          runtime <- ZIO.runtime[Any]
          queue <- ZIO.acquireRelease(Queue.bounded[Take[Throwable, Byte]](queueSize))(_.shutdown)
          // Note that we need to close the result from `makeOutputStream`. Therefore, `makeOutputStream` can not be
          // executed as a part of `streamWriter`.
          _ <- ZIO
                 .attemptBlocking {
                   val queueOutputStream = new BufferedOutputStream(new QueueOutputStream(runtime, queue), chunkSize)
                   makeOutputStream(queueOutputStream)
                 }
                 .flatMap { outputStream =>
                   streamWriter(stream, outputStream)
                     .onDoneCause(
                       cause => queue.offer(Take.failCause(cause)),
                       _ => ZIO.attemptBlocking(outputStream.close()).orDie,
                     )
                 }
                 .forkScoped
        } yield ZStream.fromQueue(queue).flattenTake
      }
    }
}

private[compress] class QueueOutputStream[E](runtime: Runtime[Any], queue: Queue[Take[E, Byte]]) extends OutputStream {
  override def write(b: Int): Unit =
    offer(Take.single(b.toByte))

  override def write(b: Array[Byte]): Unit =
    offer(Take.chunk(Chunk.fromArray(java.util.Arrays.copyOf(b, b.length))))

  override def write(b: Array[Byte], off: Int, len: Int): Unit =
    offer(Take.chunk(Chunk.fromArray(java.util.Arrays.copyOfRange(b, off, off + len))))

  override def close(): Unit =
    offer(Take.end)

  private def offer(take: Take[Nothing, Byte]): Unit = {
    val _ = Unsafe.unsafe { implicit unsafe =>
      runtime.unsafe.run {
        queue.offer(take)
      }
    }
  }
}
