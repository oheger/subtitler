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
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.stream.stage.{GraphStage, GraphStageLogic, OutHandler}
import org.apache.pekko.stream.{ActorAttributes, Attributes, Outlet, SourceShape}
import org.apache.pekko.util.ByteString

import javax.sound.sampled.{AudioFormat, AudioSystem, DataLine, TargetDataLine}
import scala.concurrent.{ExecutionContext, Future}

/**
  * An object providing a stream [[Source]] to capture audio from a microphone.
  *
  * The object provides a factory for creating a new [[Source]] that expects
  * the name of the mixer to be used (in case there are multiple input devices
  * available). The source then opens a [[TargetDataLine]] from this mixer and
  * obtains audio data from it which is passed downstream. Since access to the
  * data line is a blocking operation, the source runs on a special blocking
  * dispatcher.
  */
object CaptureAudioSource:
  /**
    * The audio format used for capturing data from the microphone. This format
    * is compatible with the requirements of the library for speech
    * recognition.
    */
  final val CaptureFormat = new AudioFormat(
    AudioFormat.Encoding.PCM_SIGNED,
    16000f,
    16,
    1,
    2,
    16000f,
    false
  )

  /**
    * The name of the dispatcher to be used for the capture audio source. Since
    * the source performs blocking operations, this should not be the default
    * dispatcher used by Pekko Streams.
    */
  final val BlockingDispatcherName = "pekko.stream.blocking-io-dispatcher"

  /**
    * An internal [[Source]] implementation that obtains its data from a
    * [[TargetDataLine]].
    *
    * @param line the underlying line
    */
  private class TargetDataLineSource(line: TargetDataLine)
    extends GraphStage[SourceShape[ByteString]]:
    private val out = Outlet[ByteString]("TargetDataLineSource.out")

    override def shape: SourceShape[ByteString] = SourceShape.of(out)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape):
      /** A buffer to query data from the line. */
      private val buffer = new Array[Byte](line.getBufferSize / 4)

      setHandler(out, new OutHandler:
        override def onPull(): Unit =
          val bytesRead = line.read(buffer, 0, buffer.length)
          if bytesRead < 0 then
            completeStage()
          else
            push(out, ByteString(buffer.take(bytesRead)))
      )

      override def postStop(): Unit =
        line.stop()
        line.close()
        super.postStop()
  end TargetDataLineSource

  /**
    * Definition of a trait for creating new source instances.
    */
  trait Factory:
    /**
      * Creates a new [[Source]] to obtain audio input from a mixer with a
      * given name. The function obtains a data line from this mixer
      * asynchronously and returns a [[Future]] with the result. The future
      * will typically not fail; in case of an error, the function returns a
      * [[Source]] that directly fails the stream.
      *
      * @param mixerName the name of the desired mixer
      * @param ec        the execution context
      * @return a [[Future]] with the [[Source]] to capture audio from the
      *         selected mixer
      */
    def apply(mixerName: String)(using ec: ExecutionContext): Future[Source[ByteString, NotUsed]]

  final val factory: Factory = new Factory:
    override def apply(mixerName: String)(using ec: ExecutionContext): Future[Source[ByteString, NotUsed]] = Future:
      val mixerInfos = AudioSystem.getMixerInfo
      val mixerInfo = mixerInfos.find(_.getName == mixerName) match
        case None =>
          val msg = mixerInfos.mkString(s"Mixer '$mixerName' not found in: ", ",", "")
          throw new IllegalStateException(msg)
        case Some(info) => info

      val mixer = AudioSystem.getMixer(mixerInfo)
      val line = mixer.getLine(new DataLine.Info(classOf[TargetDataLine], CaptureFormat)).asInstanceOf[TargetDataLine]
      line.open(CaptureFormat)
      line.start()

      Source.fromGraph(new TargetDataLineSource(line))
        .withAttributes(ActorAttributes.dispatcher(BlockingDispatcherName))
    .recover:
      case cause => Source.failed(cause)
      