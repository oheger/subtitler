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

/** The version of this project. */
lazy val VersionSubtitler = "0.0.2-SNAPSHOT"

/** Definition of versions for compile-time dependencies. */
lazy val VersionJavaFX = "21.0.8"
lazy val VersionLog4j = "2.25.2"
lazy val VersionPekko = "1.2.1"
lazy val VersionScala = "3.7.3"
lazy val VersionScalaFX = "21.0.0-R32"
lazy val VersionSprayJson = "1.3.6"
lazy val VersionVosk = "0.3.45"

/** Definition of versions for test dependencies. */
lazy val VersionScalaTest = "3.2.19"
lazy val VersionScalaTestMockito = "3.2.19.0"

/** The dependencies to Apache Pekko libraries. */
lazy val pekkoDependencies = Seq(
  "org.apache.pekko" %% "pekko-stream" % VersionPekko,
  "org.apache.pekko" %% "pekko-testkit" % VersionPekko % Test
)

/** The dependencies to the logging framework. */
lazy val logDependencies = Seq(
  "org.apache.logging.log4j" % "log4j-api" % VersionLog4j,
  "org.apache.logging.log4j" % "log4j-core" % VersionLog4j,
  "org.apache.logging.log4j" % "log4j-jcl" % VersionLog4j,
  "org.apache.logging.log4j" % "log4j-slf4j2-impl" % VersionLog4j,
)

/** The dependencies used for testing. */
lazy val testDependencies = Seq(
  "org.scalatest" %% "scalatest" % VersionScalaTest % "test",
  "org.scalatestplus" %% "mockito-5-18" % VersionScalaTestMockito % "test"
)

/**
  * Returns the platform-specific JavaFX libraries.
  *
  * @return the JavaFX dependencies for the current platform.
  */
def javaFxDependencies(): Seq[ModuleID] = {
  val javaFXClassifier = System.getProperty("os.name") match {
    case n if n.startsWith("Linux") => "linux"
    case n if n.startsWith("Mac") => "mac"
    case n if n.startsWith("Windows") => "win"
    case _ => throw new Exception("Unknown platform!")
  }
  Seq("base", "controls", "fxml", "graphics", "media", "swing", "web")
    .map(m => "org.openjfx" % s"javafx-$m" % VersionJavaFX classifier javaFXClassifier)
}

scalacOptions ++=
  Seq(
    "-feature",
    "-deprecation",
    "-unchecked",
    "-Xfatal-warnings",
    "-new-syntax"
  )

lazy val Subtitler = (project in file("."))
  .settings(
    version := VersionSubtitler,
    scalaVersion := VersionScala,
    libraryDependencies += "com.alphacephei" % "vosk" % VersionVosk,
    libraryDependencies += "io.spray" %% "spray-json" % VersionSprayJson,
    libraryDependencies += "org.scalafx" %% "scalafx" % VersionScalaFX,
    libraryDependencies ++= javaFxDependencies(),
    libraryDependencies ++= pekkoDependencies,
    libraryDependencies ++= logDependencies,
    libraryDependencies ++= testDependencies,
    name := "subtitler",
    Test / fork := true,
    Compile / mainClass := Some("com.github.oheger.subtitler.ui.UiMain"),
    assembly / assemblyMergeStrategy := {
      case PathList(ps @ _*) if ps.startsWith(List("META-INF", "substrate", "config")) => MergeStrategy.discard
      case PathList(ps @ _*) if ps.last == "module-info.class" => MergeStrategy.discard
      case x =>
        val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
        oldStrategy(x)
    }
  )
