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

import com.github.oheger.subtitler.stream.SpeechRecognizerStream
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Sink

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Failure

object Subtitler:
  def main(args: Array[String]): Unit =
    if args.length != 2 then
      println("Usage: subtitler <model> <mixer>")
      println("  <model>: path to the extracted model files")
      println("  <mixer>: the name of the mixer to capture audio from")
      System.exit(1)

    val system = ActorSystem("Subtitler")

    given ActorSystem = system

    val sink = Sink.foreach[String](println)
    val streamHandle = SpeechRecognizerStream.run(args(1), args.head, sink)

    println("Capturing audio...")
    Await.ready(streamHandle.materializedValue, Duration.Inf)

    streamHandle.materializedValue.value match
      case Some(Failure(exception)) =>
        println("Stream for speech recognition failed:")
        exception.printStackTrace()
      case _ =>
        println("Stream for speech recognition completed.")

    Await.result(system.terminate(), Duration.Inf)
