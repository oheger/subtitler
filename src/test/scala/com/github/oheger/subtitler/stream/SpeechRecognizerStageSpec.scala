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
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.testkit.TestKit
import org.apache.pekko.util.ByteString
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AsyncFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar

import scala.concurrent.Future
import scala.util.{Failure, Success}

object SpeechRecognizerStageSpec:
  /** The path to the data of the test model. */
  private val ModelPath = "my/model/data"

  /**
    * A prefix for audio data that is recognized by the mock speech converter.
    */
  private val AudioInputPrefix = "AudioData_"

  /**
    * A prefix to indicate that audio data has been processed by the mock
    * speech converter.
    */
  private val ConvertedAudioInputPrefix = "processed_"

  /**
    * Generates test audio data based on the given index that can be handled by
    * the mock speech converter.
    *
    * @param idx the index
    * @return the generated audio data chunk
    */
  private def testAudioData(idx: Int): ByteString =
    ByteString(AudioInputPrefix + idx)

  /**
    * Generates a string simulating extracted text from a test audio data
    * chunk.
    *
    * @param idx the index of the test audio chunk
    * @return the text to be extracted by the mock speech converter
    */
  private def processedAudioData(idx: Int): String =
    s"$ConvertedAudioInputPrefix${testAudioData(idx).utf8String}"
end SpeechRecognizerStageSpec

/**
  * Test class for [[SpeechRecognizerStage]].
  */
class SpeechRecognizerStageSpec(testSystem: ActorSystem) extends TestKit(testSystem) with AsyncFlatSpecLike
  with BeforeAndAfterAll with Matchers with MockitoSugar:
  def this() = this(ActorSystem("SpeechRecognizerStageSpec"))

  override protected def afterAll(): Unit =
    TestKit.shutdownActorSystem(system)
    super.afterAll()

  import SpeechRecognizerStageSpec.*

  /**
    * Creates a mock factory for a converter that allows injecting the given
    * converter mock into the test stage.
    *
    * @param converter the converter to be used by the stage
    * @return the mock converter factory
    */
  private def converterFactory(converter: SpeechConverter): SpeechConverter.Factory =
    val converterFactory = mock[SpeechConverter.Factory]
    when(converterFactory(ModelPath)).thenReturn(Success(converter))
    converterFactory

  /**
    * Creates a mock for a [[SpeechConverter]]. The mock is prepared to extract
    * text from specific input data; for other input, it returns ''None''.
    *
    * @return the prepared [[SpeechConverter]] mock
    */
  private def createConverterMock(): SpeechConverter =
    val converter = mock[SpeechConverter]
    when(converter.convert(any())).thenAnswer((invocation: InvocationOnMock) =>
      val audioData = invocation.getArgument(0, classOf[ByteString]).utf8String
      if audioData.startsWith(AudioInputPrefix) then
        Some(ConvertedAudioInputPrefix + audioData)
      else
        None)
    when(converter.finalResult).thenReturn(None)
    converter

  /**
    * Executes a stream with the given source data and a test recognizer stage
    * that uses the given mock [[SpeechConverter]]. Returns the data produced
    * by the test stage.
    *
    * @param audioData the source audio data
    * @param converter the mock for the converter
    * @return a [[Future]] with the results of stream processing
    */
  private def runRecognizerStream(audioData: Iterable[ByteString], converter: SpeechConverter): Future[List[String]] =
    val source = Source(audioData.toList)
    val sink = Sink.fold[List[String], String](Nil): (lst, s) =>
      s :: lst
    for
      stage <- SpeechRecognizerStage.factory(ModelPath, converterFactory(converter))
      result <- source.via(stage).runWith(sink)
    yield
      result.reverse

  "A SpeechRecognizerStage" should "extract text from audio input" in :
    val ChunkCount = 16
    val input = (1 to ChunkCount).map(testAudioData)
    val expectedResult = (1 to ChunkCount).map(processedAudioData)

    runRecognizerStream(input, createConverterMock()) map : result =>
      result should contain theSameElementsInOrderAs expectedResult

  it should "handle unavailable results" in :
    val input = List(
      ByteString("some data"),
      testAudioData(1),
      ByteString("some other data"),
      testAudioData(2),
      ByteString("and more other data")
    )
    val expectedResult = List(processedAudioData(1), processedAudioData(2))

    runRecognizerStream(input, createConverterMock()) map : result =>
      result should contain theSameElementsInOrderAs expectedResult

  it should "handle a final result" in :
    val FinalResult = "This is the last extracted text"
    val input = List(testAudioData(1), testAudioData(2), testAudioData(3))
    val expectedResult = List(processedAudioData(1), processedAudioData(2), processedAudioData(3), FinalResult)
    val converter = createConverterMock()
    when(converter.finalResult).thenReturn(Some(FinalResult))

    runRecognizerStream(input, converter) map : result =>
      result should contain theSameElementsInOrderAs expectedResult

  it should "close the converter when the stream completes" in :
    val converter = createConverterMock()

    runRecognizerStream(List(ByteString("some data"), testAudioData(42)), converter) map : _ =>
      verify(converter).close()
      succeed

  it should "fail the stream if the factory thrown an exception" in :
    val exception = new IllegalStateException("Test exception: Could not create SpeechConverter")
    val converterFactory = mock[SpeechConverter.Factory]
    when(converterFactory(ModelPath)).thenReturn(Failure(exception))

    SpeechRecognizerStage.factory(ModelPath, converterFactory) flatMap : stage =>
      val source = Source.single(testAudioData(7))
      val sink = Sink.last[String]
      recoverToExceptionIf[IllegalStateException]:
        source.via(stage).runWith(sink)
      .map: streamException =>
        streamException should be(exception)
