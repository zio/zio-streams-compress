package zio.compress

import zio.{Scope, ZIO}

import java.io.IOException
import java.nio.file.{FileVisitResult, Files, Path, SimpleFileVisitor}
import java.nio.file.attribute.BasicFileAttributes

object TempDir {

  /**
   * Create a temporary directory for testing purposes.
   * The directory is deleted once the test's scope completes.
   *
   * Example:
   * {{{
   *   test("test something") {
   *     for {
   *       tmpDir <- temporaryDirectory
   *       // use tmpDir
   *     } yield {
   *       assertTrue(true)
   *     }
   *   }
   * }}}
   */
  def temporaryDirectory: ZIO[Scope, IOException, Path] =
    ZIO.acquireRelease(createTempDir)(dir => deleteDirRecursive(dir).orDie)

  private def createTempDir: ZIO[Any, IOException, Path] =
    ZIO.attemptBlockingIO(Files.createTempDirectory("ziotest"))

  private def deleteDirRecursive(path: Path): ZIO[Any, IOException, Unit] = ZIO.attemptBlockingIO {
    if (Files.exists(path)) {
      val _ = Files.walkFileTree(
        path,
        new SimpleFileVisitor[Path]() {
          override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
            Files.delete(file)
            FileVisitResult.CONTINUE
          }

          override def postVisitDirectory(dir: Path, exc: IOException): FileVisitResult = {
            Files.delete(dir)
            FileVisitResult.CONTINUE
          }
        },
      )
    }
  }.unit

}
