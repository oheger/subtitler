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

package com.github.oheger.subtitler.stream

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.KillSwitches
import org.apache.pekko.stream.scaladsl.Sink

import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.Future

/**
  * An object that allows running a stream which extracts text from audio
  * input.
  *
  * This is the main entry point into this package. Based on provided
  * parameters, the object sets up a source to capture audio and a speech
  * recognizer stage. The stream is then started with a configurable sink.
  */
object SpeechRecognizerStream:
  /**
    * A trait representing a handle to a speech recognizer stream. With the
    * handle, clients can cancel the stream and obtain the materialized value.
    *
    * @tparam MAT the type of the materialized value
    */
  trait StreamHandle[MAT]:
    /**
      * Returns the materialized value produced by the stream. This is
      * a future that can be used to determine when the stream completes.
      *
      * @return a [[Future]] with the materialized value of the stream
      */
    def materializedValue: Future[MAT]

    /**
      * Allows canceling the stream.
      */
    def cancel(): Unit
  end StreamHandle

  /**
    * A trait that can be used to start a new recognizer stream.
    */
  trait Runner:
    /**
      * Starts a speech recognizer stream and returns a [[StreamHandle]] to
      * control it.
      *
      * @param mixerName         the name of the mixer to capture audio
      * @param modelPath         the path to the speech model
      * @param sink              a [[Sink]] where to pass extracted text
      * @param sourceFactory     the factory to create the capture source
      * @param recognizerFactory the factory for the recognizer stage
      * @param system            the actor system
      * @tparam MAT the type of the materialized value of the sink
      * @return a handle to control the stream
      */
    def apply[MAT](mixerName: String,
                   modelPath: String,
                   sink: Sink[String, Future[MAT]],
                   sourceFactory: CaptureAudioSource.Factory = CaptureAudioSource.factory,
                   recognizerFactory: SpeechRecognizerStage.Factory = SpeechRecognizerStage.factory)
                  (using system: ActorSystem): StreamHandle[MAT]
  end Runner

  /**
    * A counter for the streams that have been started. This is used to
    * generate unique names.
    */
  private val streamCounter = new AtomicLong

  /**
    * A default [[Runner]] implementation to start new speech recognizer
    * streams.
    */
  final val run: Runner = new Runner:
    override def apply[MAT](mixerName: String,
                            modelPath: String,
                            sink: Sink[String, Future[MAT]],
                            sourceFactory: CaptureAudioSource.Factory,
                            recognizerFactory: SpeechRecognizerStage.Factory)
                           (using system: ActorSystem): StreamHandle[MAT] =
      import system.dispatcher
      val futSource = sourceFactory(mixerName)
      val futStage = recognizerFactory(modelPath)
      val killSwitch = KillSwitches.shared(s"speechRecognizerStream${streamCounter.incrementAndGet()}_ks")
      val futStream = for
        source <- futSource
        stage <- futStage
        result <- source.via(killSwitch.flow).via(stage).runWith(sink)
      yield result

      new StreamHandle[MAT]:
        override val materializedValue: Future[MAT] = futStream

        override def cancel(): Unit =
          killSwitch.shutdown()
