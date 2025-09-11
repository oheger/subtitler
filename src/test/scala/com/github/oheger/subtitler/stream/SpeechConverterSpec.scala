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

import org.apache.pekko.util.ByteString
import org.mockito.ArgumentMatchers.any
import org.mockito.MockedConstruction
import org.mockito.Mockito.*
import org.scalatest.{OptionValues, TryValues}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.vosk.{Model, Recognizer}

import java.util.concurrent.atomic.AtomicReference
import scala.jdk.CollectionConverters.*
import scala.util.{Random, Using}

/**
  * Test class for [[SpeechConverter]].
  */
class SpeechConverterSpec extends AnyFlatSpec with Matchers with MockitoSugar with TryValues with OptionValues:
  "voskFactory" should "create a correct model" in :
    val ModelPath = "path/to/model"
    val modelMock = new AtomicReference[Model]
    val initializer: MockedConstruction.MockInitializer[Model] = (mock, context) =>
      context.arguments().asScala should contain only ModelPath
      modelMock.set(mock)

    Using(mockConstruction(classOf[Model], initializer)): _ =>
      val model = SpeechConverter.voskFactory.createModel(ModelPath)
      model should be(modelMock.get())
    .success

  it should "create a correct Recognizer" in :
    val model = mock[Model]
    val recognizerMock = new AtomicReference[Recognizer]
    val initializer: MockedConstruction.MockInitializer[Recognizer] = (mock, context) =>
      context.arguments().asScala should contain theSameElementsInOrderAs List(model, 16000f)
      recognizerMock.set(mock)

    Using(mockConstruction(classOf[Recognizer], initializer)): _ =>
      val recognizer = SpeechConverter.voskFactory.createRecognizer(model)
      recognizer should be(recognizerMock.get())
    .success

  "A SpeechConverter" should "close the vosk objects" in :
    val model = mock[Model]
    val recognizer = mock[Recognizer]

    val speechConverter = new SpeechConverter(model, recognizer)
    speechConverter.close()

    verify(model).close()
    verify(recognizer).close()

  it should "convert audio data if no results are available" in :
    val audioData = Random.nextBytes(2048)
    val recognizer = mock[Recognizer]
    doReturn(false).when(recognizer).acceptWaveForm(audioData, audioData.length)

    val speechConverter = new SpeechConverter(mock, recognizer)
    speechConverter.convert(ByteString(audioData)) shouldBe empty

    verify(recognizer).acceptWaveForm(audioData, audioData.length)

  it should "convert audio data and return results" in :
    val audioData = Random.nextBytes(2048)
    val text = "This is the recognized text"
    val jsonText =
      s"""{
         | "text": "$text"
         |}
         |""".stripMargin
    val recognizer = mock[Recognizer]
    doReturn(true).when(recognizer).acceptWaveForm(audioData, audioData.length)
    doReturn(jsonText).when(recognizer).getResult

    val speechConverter = new SpeechConverter(mock, recognizer)
    val result = speechConverter.convert(ByteString(audioData))

    result.value should be(text)

  it should "handle a result without a 'text' property" in :
    val audioData = Random.nextBytes(512)
    val jsonText =
      """{
        | "someField": "some data"
        |}
        |""".stripMargin
    val recognizer = mock[Recognizer]
    doReturn(true).when(recognizer).acceptWaveForm(audioData, audioData.length)
    doReturn(jsonText).when(recognizer).getResult

    val speechConverter = new SpeechConverter(mock, recognizer)
    val result = speechConverter.convert(ByteString(audioData))

    result shouldBe empty

  it should "handle an empty result" in :
    val audioData = Random.nextBytes(512)
    val jsonText =
      """{
        | "text": ""
        |}
        |""".stripMargin
    val recognizer = mock[Recognizer]
    doReturn(true).when(recognizer).acceptWaveForm(audioData, audioData.length)
    doReturn(jsonText).when(recognizer).getResult

    val speechConverter = new SpeechConverter(mock, recognizer)
    val result = speechConverter.convert(ByteString(audioData))

    result shouldBe empty

  it should "handle an empty string as final result" in :
    val recognizer = mock[Recognizer]
    doReturn("").when(recognizer).getFinalResult

    val speechConverter = new SpeechConverter(mock, recognizer)
    val result = speechConverter.finalResult

    result shouldBe empty

  it should "handle null as final result" in :
    val recognizer = mock[Recognizer]
    doReturn("null").when(recognizer).getFinalResult

    val speechConverter = new SpeechConverter(mock, recognizer)
    val result = speechConverter.finalResult

    result shouldBe empty

  it should "handle a final result without a 'text' property" in :
    val jsonText =
      """{
        | "someField": "some data"
        |}
        |""".stripMargin
    val recognizer = mock[Recognizer]
    doReturn(jsonText).when(recognizer).getFinalResult

    val speechConverter = new SpeechConverter(mock, recognizer)
    val result = speechConverter.finalResult

    result shouldBe empty

  it should "handle an empty final result" in :
    val jsonText =
      """{
        | "text": ""
        |}
        |""".stripMargin
    val recognizer = mock[Recognizer]
    doReturn(jsonText).when(recognizer).getFinalResult

    val speechConverter = new SpeechConverter(mock, recognizer)
    val result = speechConverter.finalResult

    result shouldBe empty

  it should "return a final result if available" in :
    val text = "This is the final recognized text"
    val jsonText =
      s"""{
         | "text": "$text"
         |}
         |""".stripMargin
    val recognizer = mock[Recognizer]
    doReturn(jsonText).when(recognizer).getFinalResult

    val speechConverter = new SpeechConverter(mock, recognizer)
    val result = speechConverter.finalResult

    result.value should be(text)

  "factory" should "create a new converter instance" in :
    val ModelPath = "model/data"
    val model = mock[Model]
    val recognizer = mock[Recognizer]
    val voskFactory = mock[SpeechConverter.VoskFactory]
    when(voskFactory.createModel(ModelPath)).thenReturn(model)
    when(voskFactory.createRecognizer(model)).thenReturn(recognizer)

    val converter = SpeechConverter.factory(ModelPath, voskFactory = voskFactory).success.value

    converter.close()
    verify(model).close()
    verify(recognizer).close()

  it should "return a failure if model creation fails" in :
    val exception = new IllegalStateException("Test exception: Cannot create Model.")
    val voskFactory = mock[SpeechConverter.VoskFactory]
    when(voskFactory.createModel(any())).thenThrow(exception)

    val actException = SpeechConverter.factory("somePath", voskFactory = voskFactory).failure.exception

    actException should be(exception)

  it should "close the model if the creation of the recognizer fails" in :
    val ModelPath = "model/data"
    val exception = new IllegalStateException("Test exception: Cannot create Recognizer.")
    val model = mock[Model]
    val voskFactory = mock[SpeechConverter.VoskFactory]
    when(voskFactory.createModel(ModelPath)).thenReturn(model)
    when(voskFactory.createRecognizer(model)).thenThrow(exception)

    val actException = SpeechConverter.factory(ModelPath, voskFactory = voskFactory).failure.exception

    verify(model).close()
    actException should be(exception)
