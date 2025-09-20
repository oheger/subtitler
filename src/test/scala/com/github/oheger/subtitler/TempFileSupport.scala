/*
 * Copyright 2025 The Developers Team.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.oheger.subtitler

import org.scalatest.{BeforeAndAfterEach, Suite}

import java.io.IOException
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{FileVisitResult, Files, Path, SimpleFileVisitor}
import scala.compiletime.uninitialized

object TempFileSupport:
  /** The prefix for temporary files created by this trait. */
  final val TempFilePrefix = "TempFileSupport"

  /** The suffix to use for temporary files. */
  final val TempFileSuffix = ".tmp"
end TempFileSupport

/**
  * A helper trait which can be used by test classes that need access to a file
  * with defined test data.
  *
  * This trait allows creating a test file. The file can be either be filled
  * initially with test data (for tests of reading functionality) or be read
  * later (for tests of writing functionality). There is also a cleanup method
  * for removing the file when it is no longer needed.
  */
trait TempFileSupport extends BeforeAndAfterEach:
  this: Suite =>

  import TempFileSupport.*

  /** The temporary directory managed by this trait. */
  var testDirectory: Path = uninitialized

  override protected def beforeEach(): Unit =
    super.beforeEach()
    testDirectory = Files.createTempDirectory(TempFilePrefix)

  override protected def afterEach(): Unit =
    if testDirectory != null then
      deleteDirectory(testDirectory)
    super.afterEach()

  /**
    * Deletes a directory and all its content.
    *
    * @param path the directory to be deleted
    */
  def deleteDirectory(path: Path): Unit =
    Files.walkFileTree(path, new SimpleFileVisitor[Path]:
      override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult =
        Files.delete(file)
        FileVisitResult.CONTINUE

      override def postVisitDirectory(dir: Path, exc: IOException): FileVisitResult =
        if exc == null then
          Files.delete(dir)
          FileVisitResult.CONTINUE
        else
          throw exc
    )

  /**
    * Creates a new temporary file under the managed test directory. The file
    * is going to be removed after the test case completes.
    *
    * @return the path to the new temporary file
    */
  def newTempFile(): Path =
    Files.createTempFile(testDirectory, TempFilePrefix, TempFileSuffix)
    