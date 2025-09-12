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

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.{Attributes, FlowShape, Inlet, Outlet}
import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import org.apache.pekko.util.ByteString

import scala.concurrent.{ExecutionContext, Future}

/**
  * An object providing a flow stage that performs speech recognition.
  *
  * The stage uses a [[SpeechConverter]] instance to convert speech to text. It
  * passes audio data from upstream to the converter, and sends extracted text
  * downstream.
  */
object SpeechRecognizerStage:
  /**
    * Definition of a factory trait for creating [[Flow]] instances for text
    * recognition. The factory method loads a speech model asynchronously and
    * constructs the flow which performs the recognition.
    */
  trait Factory:
    /**
      * Creates a new [[Flow]] to perform speech recognition based on a 
      * [[SpeechConverter]] that is created using the given factory. If the
      * creation of the converter fails, the resulting [[Future]] is still 
      * successful, but the created flow stage will immediately fail the stream
      * with a corresponding exception.
      *
      * @param modelPath        the path to the model data
      * @param converterFactory factory to create a [[SpeechConverter]]
      * @param ec               the execution context
      * @return a [[Future]] with the text recognition flow stage
      */
    def apply(modelPath: String, converterFactory: SpeechConverter.Factory = SpeechConverter.factory)
             (using ec: ExecutionContext): Future[Flow[ByteString, String, NotUsed]]

  /**
    * A default factory instance for creating new instances of the recognizer
    * stage.
    */
  final val factory: Factory = new Factory:
    override def apply(modelPath: String, converterFactory: SpeechConverter.Factory)
                      (using ec: ExecutionContext): Future[Flow[ByteString, String, NotUsed]] = Future:
      converterFactory(modelPath)
        .map(converter => Flow.fromGraph(new SpeechRecognizerStage(converter)))
        .recover:
          case exception => Flow[ByteString].map(_ => throw exception)
        .get
end SpeechRecognizerStage

/**
  * The implementation of the stage that uses a [[SpeechConverter]] to extract
  * text from audio input.
  *
  * @param converter the [[SpeechConverter]] to be used by the stage
  */
private class SpeechRecognizerStage(converter: SpeechConverter) extends GraphStage[FlowShape[ByteString, String]]:
  val in = Inlet[ByteString]("SpeechRecognizerStage.in")
  val out = Outlet[String]("SpeechRecognizerStage.out")

  override def shape: FlowShape[ByteString, String] = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape):
    /** A flag to record that upstream has finished. */
    private var streamComplete = false

    /** Stores the final result from the converter. */
    private var finalResult: Option[String] = None

    setHandler(in, new InHandler:
      override def onPush(): Unit =
        converter.convert(grab(in)) match
          case Some(result) =>
            push(out, result)
          case None =>
            pull(in)

      override def onUpstreamFinish(): Unit =
        streamComplete = true
        converter.finalResult match
          case optRes@Some(result) =>
            if isAvailable(out) then
              push(out, result)
            else
              finalResult = optRes
          case None =>
            super.onUpstreamFinish()
    )

    setHandler(out, new OutHandler:
      override def onPull(): Unit =
        if streamComplete then
          finalResult match
            case Some(result) =>
              push(out, result)
              finalResult = None
            case None =>
              completeStage()
        else
          pull(in)
    )

    override def postStop(): Unit =
      converter.close()
      super.postStop()
