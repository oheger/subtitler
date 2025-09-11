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

import com.github.oheger.subtitler.stream.SpeechConverter.deserializeRecognizerResult
import org.apache.pekko.util.ByteString
import org.vosk.{Model, Recognizer}
import spray.json.*

import scala.util.{Failure, Success, Try}

object SpeechConverter extends DefaultJsonProtocol:
  /**
    * A trait that allows abstracting over the creation of the Vosk objects
    * that are needed for speech recognition. It also supports testability by
    * allowing the injection of mock objects.
    */
  trait VoskFactory:
    /**
      * Creates a [[Model]] object from the data stored at the specified local
      * path.
      *
      * @param path the path to the model data
      * @return the [[Model]] object
      */
    def createModel(path: String): Model

    /**
      * Creates a [[Recognizer]] from the given model.
      *
      * @param model the [[Model]]
      * @return the [[Recognizer]]
      */
    def createRecognizer(model: Model): Recognizer
  end VoskFactory

  /**
    * A default [[VoskFactory]] instance which is used by the factory for the
    * speech converter.
    */
  final val voskFactory: VoskFactory = new VoskFactory:
    override def createModel(path: String): Model = new Model(path)

    override def createRecognizer(model: Model): Recognizer =
      new Recognizer(model, CaptureAudioSource.CaptureFormat.getSampleRate)

  /**
    * Definition of a factory trait for creating converter instances.
    */
  trait Factory:
    /**
      * Tries to create a new instance of [[SpeechConverter]] using the given
      * path to model data. Returns a [[Failure]] if the creation fails; in
      * this case, all resources are released.
      *
      * @param modelPath the path to the model data
      * @return a [[Try]] with the newly created instance
      */
    def apply(modelPath: String, voskFactory: VoskFactory = voskFactory): Try[SpeechConverter]
  end Factory

  /**
    * A default [[Factory]] instance for creating new [[SpeechConverter]]
    * instances.
    */
  final val factory: Factory = (path, objectFactory) => Try:
    val model = objectFactory.createModel(path)
    Try(objectFactory.createRecognizer(model)) match
      case Success(recognizer) =>
        new SpeechConverter(model, recognizer)
      case Failure(exception) =>
        model.close()
        throw exception

  /**
    * A data class representing a result from the Vosk [[Recognizer]]. This is
    * used for JSON deserialization.
    *
    * @param text the optional text detected by the recognizer
    */
  private case class RecognizerResult(text: Option[String])

  /** Format to deserialize a [[RecognizerResult]]. */
  private given recognizerResultFormat: RootJsonFormat[RecognizerResult] = jsonFormat1(RecognizerResult.apply)

  /**
    * Extracts the recognized text from a JSON result returned by the
    * [[Recognizer]]. Handles various corner cases like invalid JSON, missing
    * properties, or empty text.
    *
    * @param result the JSON result returned by the [[Recognizer]]
    * @return an [[Option]] with extracted text
    */
  private def deserializeRecognizerResult(result: String): Option[String] = Try:
    val json = result.parseJson
    json.convertTo[RecognizerResult].text.filter(_.nonEmpty)
  .getOrElse(None)
end SpeechConverter

/**
  * A class wrapping the Vosk API for speech recognition. The class can be fed
  * with audio data and returns the recognized text. It manages the Vosk
  * objects that are required for this conversion.
  *
  * @param model      the speech model
  * @param recognizer the object to recognize speech
  */
class SpeechConverter(model: Model,
                      recognizer: Recognizer) extends AutoCloseable:
  /**
    * @inheritdoc This implementation closes the managed objects.
    */
  override def close(): Unit =
    recognizer.close()
    model.close()

  /**
    * Converts a chunk of audio data to text if possible. If no results are
    * available yet, result is ''None''; in this case, further data is
    * required.
    *
    * @param audioData the chunk of audio data
    * @return an [[Option]] with the recognized text
    */
  def convert(audioData: ByteString): Option[String] =
    if recognizer.acceptWaveForm(audioData.toArray, audioData.length) then
      deserializeRecognizerResult(recognizer.getResult)
    else
      None

  /**
    * Returns final conversion results if available. This method should be
    * called at the end of the audio stream to get results that have not been
    * delivered previously.
    *
    * @return an [[Option]] with final recognized text
    */
  def finalResult: Option[String] =
    deserializeRecognizerResult(recognizer.getFinalResult)
