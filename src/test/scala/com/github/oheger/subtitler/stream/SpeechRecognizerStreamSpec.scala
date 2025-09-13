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
import org.apache.pekko.stream.scaladsl.{Flow, Sink, Source}
import org.apache.pekko.testkit.TestKit
import org.apache.pekko.util.ByteString
import org.mockito.ArgumentMatchers.{any, eq as eqArg}
import org.mockito.Mockito.*
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AsyncFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar

import scala.concurrent.Future

object SpeechRecognizerStreamSpec:
  /** The path to the model used by the tests. */
  private val ModelPath = "/path/to/test/model"

  /** The name of the mixer used by the tests. */
  private val MixerName = "my-test-mixer"
end SpeechRecognizerStreamSpec

/**
  * Test class for [[SpeechRecognizerStream]].
  */
class SpeechRecognizerStreamSpec(testSystem: ActorSystem) extends TestKit(testSystem) with AsyncFlatSpecLike
  with BeforeAndAfterAll with Matchers with MockitoSugar:
  def this() = this(ActorSystem("SpeechRecognizerStreamSpec"))

  override protected def afterAll(): Unit =
    TestKit.shutdownActorSystem(system)
    super.afterAll()

  import SpeechRecognizerStreamSpec.*

  /**
    * Starts a recognizer stream with the given input source and returns the
    * handle to the stream. The sink collects the data flowing through the
    * stream in reverse order.
    *
    * @param source the source for the stream
    * @return the handle to the stream
    */
  private def createStream(source: Source[ByteString, Any]): SpeechRecognizerStream.StreamHandle[List[String]] =
    val sourceFactory = mock[CaptureAudioSource.Factory]
    when(sourceFactory.apply(eqArg(MixerName))(using any())).thenReturn(Future.successful(source))
    val stageFactory = mock[SpeechRecognizerStage.Factory]
    val stubRecognizerStage = Flow[ByteString].map(_.utf8String)
    when(stageFactory.apply(eqArg(ModelPath), any())(using any())).thenReturn(Future.successful(stubRecognizerStage))
    val sink = Sink.fold[List[String], String](Nil): (lst, s) =>
      s :: lst
    SpeechRecognizerStream.run(
      mixerName = MixerName,
      modelPath = ModelPath,
      sourceFactory = sourceFactory,
      recognizerFactory = stageFactory,
      sink = sink
    )

  "SpeechRecognizerStream" should "correctly run a stream" in :
    val data = List("This", "is", "test", "data", "flowing", "through", "the", "stream")

    val handle = createStream(Source(data.map(ByteString.apply)))
    handle.materializedValue map : results =>
      results should contain theSameElementsInOrderAs data.reverse

  it should "handle failures during stream execution" in :
    val exception = new IllegalStateException("Test exception: Failure during stream execution.")

    val handle = createStream(Source.failed(exception))
    recoverToExceptionIf[IllegalStateException](handle.materializedValue) map : cause =>
      cause should be(exception)

  it should "allow canceling the stream" in :
    val source = Source.repeat(ByteString("This is repeated test data."))
    val handle = createStream(source)

    handle.cancel()
    handle.materializedValue.map(_ => succeed)
