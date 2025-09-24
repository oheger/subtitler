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

package com.github.oheger.subtitler.ui

import com.github.oheger.subtitler.TempFileSupport
import com.github.oheger.subtitler.config.SubtitlerConfig
import com.github.oheger.subtitler.stream.SpeechRecognizerStream
import javafx.collections.FXCollections
import javafx.stage.Window as JfxWindow
import org.apache.pekko.Done
import org.apache.pekko.actor.{ActorSystem, Terminated}
import org.mockito.ArgumentMatchers.{any, eq as eqArg}
import org.mockito.Mockito.*
import org.mockito.{MockedConstruction, MockedStatic}
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterEach, TryValues}
import org.scalatestplus.mockito.MockitoSugar
import scalafx.beans.property.{ObjectProperty, StringProperty}
import scalafx.stage.{DirectoryChooser, Window}

import java.io.File
import java.util.concurrent.{CountDownLatch, TimeUnit}
import javax.sound.sampled.{AudioSystem, Mixer}
import scala.compiletime.uninitialized
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Using

object ControllerSpec:
  /** The names of the mixers supported by the mock audio system. */
  private val Mixers = List("Test Mixer 1", "Test Mixer 2", "Another Test Mixer")
end ControllerSpec

/**
  * Test class for [[Controller]].
  */
class ControllerSpec extends AnyFlatSpecLike with Matchers with BeforeAndAfterEach with MockitoSugar with TryValues
  with TempFileSupport:

  import ControllerSpec.*

  /** The static mock for the Java Audio System. */
  private var mockedAudioSystem: MockedStatic[AudioSystem] = uninitialized

  override protected def beforeEach(): Unit =
    super.beforeEach()
    mockedAudioSystem = prepareAudioSystemMock()

  override protected def afterEach(): Unit =
    mockedAudioSystem.close()
    System.clearProperty(SubtitlerConfig.ConfigFileProperty)
    super.afterEach()

  /**
    * Prepares the static mock for the audio system to return mixer information
    * for the test mixers.
    *
    * @return the static mock for the audio system
    */
  private def prepareAudioSystemMock(): MockedStatic[AudioSystem] =
    val audioSystemMock = mockStatic(classOf[AudioSystem])
    initMixerInfo(Mixers, audioSystemMock)
    audioSystemMock

  /**
    * Initializes a mock for the audio system to return mixer information with
    * the given names.
    *
    * @param mixerNames      the mixer names to return
    * @param audioSystemMock the static mock for the audio system
    */
  private def initMixerInfo(mixerNames: Iterable[String],
                            audioSystemMock: MockedStatic[AudioSystem] = mockedAudioSystem): Unit =
    val mixerInfos = mixerNames.map: mixerName =>
      val info = mock[Mixer.Info]
      when(info.getName).thenReturn(mixerName)
      info
    audioSystemMock.when(() => AudioSystem.getMixerInfo).thenReturn(mixerInfos.toArray)

  /**
    * Creates a mock stream handle and prepares it for the expected
    * interactions.
    *
    * @return the mock stream handle
    */
  private def createHandleMock(): SpeechRecognizerStream.StreamHandle[Done] =
    val handle = mock[SpeechRecognizerStream.StreamHandle[Done]]
    when(handle.materializedValue).thenReturn(Future.successful(Done))
    handle

  /**
    * Creates a mock for an actor system. The mock is prepared to return its
    * dispatcher which is used as execution context by the controller.
    *
    * @return the mock actor system
    */
  private def createActorSystemMock(): ActorSystem =
    import scala.concurrent.ExecutionContext.Implicits.global
    val ec: ExecutionContext = implicitly[ExecutionContext]
    val system = mock[ActorSystem]
    when(system.dispatcher).thenReturn(ec)
    system

  /**
    * Creates a controller instance to be tested using mocks for the
    * dependencies per default.
    *
    * @param actorSystem  the actor system
    * @param synchronizer the UI synchronizer
    * @param runner       the stream runner
    * @return the controller to be tested
    */
  private def createController(actorSystem: ActorSystem = createActorSystemMock(),
                               synchronizer: UiSynchronizer = mock,
                               runner: SpeechRecognizerStream.Runner = mock): Controller =
    new Controller(
      actorSystem = actorSystem,
      synchronizer = synchronizer,
      streamRunner = runner
    )

  "A Controller" should "provide a collection with the available mixers" in :
    val expectedDevices = List("Another Test Mixer", "Test Mixer 1", "Test Mixer 2")
    val property = ObjectProperty(FXCollections.observableArrayList[String]())
    val controller = createController()
    property <==> controller.inputDevices

    controller.setUp(mock)

    property.value should contain theSameElementsInOrderAs expectedDevices

  it should "update the collection of available mixers" in :
    val property = ObjectProperty(FXCollections.observableArrayList[String]())
    val controller = createController()
    property <==> controller.inputDevices
    controller.setUp(mock)
    val updatedMixers = List("M1", "M2", "M3", "M4")
    initMixerInfo(updatedMixers)

    controller.updateInputDevices()

    property.value should contain theSameElementsInOrderAs updatedMixers

  it should "allow selecting the path to the speech model" in :
    val selectedDirectory = new File("/path/to/speech/model")
    val parent = mock[Window]
    val initializer: MockedConstruction.MockInitializer[DirectoryChooser] = (mock, context) =>
      when(mock.showDialog(parent)).thenReturn(selectedDirectory)

    Using(mockConstruction(classOf[DirectoryChooser], initializer)): c =>
      val property = StringProperty("")
      val controller = createController()
      property <==> controller.modelPath
      controller.chooseModelPath(parent)

      property.value should be(selectedDirectory.getAbsolutePath)
      val mockChooser = c.constructed().getFirst
      verify(mockChooser, never()).initialDirectory = any()
    .success

  it should "set the initial directory for the speech model" in :
    val parent = mock[Window]
    val initialFile = new File("/currently/selected/model/path")
    val initializer: MockedConstruction.MockInitializer[DirectoryChooser] = (mock, context) =>
      when(mock.showDialog(parent)).thenReturn(new File("foo"))

    Using(mockConstruction(classOf[DirectoryChooser], initializer)): c =>
      val property = StringProperty("")
      val controller = createController()
      property <==> controller.modelPath
      property.value = initialFile.getAbsolutePath
      controller.chooseModelPath(parent)

      val mockChooser = c.constructed().getFirst
      verify(mockChooser).initialDirectory = initialFile
    .success

  it should "not enable the start button if no input device is selected" in :
    val inputDeviceProperty = StringProperty("")
    val modelPathProperty = StringProperty("")
    val controller = createController()

    inputDeviceProperty <==> controller.selectedInputDevice
    modelPathProperty <==> controller.modelPath
    modelPathProperty.value = "/some/model/path"

    controller.canStartRecognizerStream.value shouldBe false

  it should "not enable the start button if no model path has been entered" in :
    val inputDeviceProperty = StringProperty("")
    val modelPathProperty = StringProperty("")
    val controller = createController()

    inputDeviceProperty <==> controller.selectedInputDevice
    modelPathProperty <==> controller.modelPath
    inputDeviceProperty.value = Mixers.head

    controller.canStartRecognizerStream.value shouldBe false

  it should "enable the start button if all criteria are met" in :
    val inputDeviceProperty = StringProperty("")
    val modelPathProperty = StringProperty("")
    val controller = createController()

    inputDeviceProperty <==> controller.selectedInputDevice
    modelPathProperty <==> controller.modelPath
    inputDeviceProperty.value = Mixers(1)
    modelPathProperty.value = "/some/model/path"

    controller.canStartRecognizerStream.value shouldBe true

  it should "not start a stream if the start button is disabled" in :
    val runner = mock[SpeechRecognizerStream.Runner]

    val controller = createController(runner = runner)
    controller.startRecognizerStream() shouldBe false

    verifyNoInteractions(runner)

  it should "start the recognizer stream" in :
    val ModelPath = "/path/to/speech/model"
    val actorSystem = createActorSystemMock()

    given ActorSystem = actorSystem

    val handle = createHandleMock()
    val runner = mock[SpeechRecognizerStream.Runner]
    when(runner.apply(any(), any(), any(), any(), any())(using any())).thenReturn(handle)

    val controller = createController(actorSystem = actorSystem, runner = runner)
    controller.selectedInputDevice.value = Mixers.head
    controller.modelPath.value = ModelPath

    controller.startRecognizerStream() shouldBe true
    verify(runner).apply(eqArg(Mixers.head), eqArg(ModelPath), any(), any(), any())(using eqArg(actorSystem))

  it should "disable the start button after starting the stream" in :
    val handle = createHandleMock()
    val runner = mock[SpeechRecognizerStream.Runner]
    when(runner.apply(any(), any(), any(), any(), any())(using any())).thenReturn(handle)

    val controller = createController(runner = runner)
    controller.selectedInputDevice.value = Mixers.head
    controller.modelPath.value = "/some/path"

    controller.startRecognizerStream() shouldBe true
    controller.canStartRecognizerStream.value shouldBe false

  it should "shutdown the actor system" in :
    val promiseShutdownComplete = Promise[Terminated]()
    val actorSystem = mock[ActorSystem]
    when(actorSystem.terminate()).thenReturn(promiseShutdownComplete.future)
    val configFile = newTempFile()
    System.setProperty(SubtitlerConfig.ConfigFileProperty, configFile.toString)

    val controller = createController(actorSystem)
    val latch = new CountDownLatch(1)
    val shutdownRunnable: Runnable = () =>
      controller.shutdown(mock)
      latch.countDown()
    val shutdownThread = new Thread(shutdownRunnable)
    shutdownThread.start()

    latch.await(100, TimeUnit.MILLISECONDS) shouldBe false
    promiseShutdownComplete.success(mock)

    latch.await(3, TimeUnit.SECONDS) shouldBe true
    shutdownThread.join()

  it should "set default properties if no configuration file is found" in :
    System.setProperty(SubtitlerConfig.ConfigFileProperty, "/non/existing/config/file")
    val controller = createController()

    val stage = mock[JfxWindow]
    controller.setUp(stage)

    controller.selectedInputDevice.value should be(SubtitlerConfig.DefaultConfig.inputDevice)
    controller.modelPath.value should be(SubtitlerConfig.DefaultConfig.modelPath)
    controller.subtitleStyles.value should be(SubtitlerConfig.DefaultConfig.subtitleStyles)
    controller.subtitleCount.value should be(SubtitlerConfig.DefaultConfig.subtitleCount)
    verify(stage).setX(SubtitlerConfig.DefaultWindowBounds.x)
    verify(stage).setY(SubtitlerConfig.DefaultWindowBounds.y)
    verify(stage).setWidth(SubtitlerConfig.DefaultWindowBounds.width)
    verify(stage).setHeight(SubtitlerConfig.DefaultWindowBounds.height)

  it should "initialize its properties from a configuration file" in :
    val config = SubtitlerConfig(
      modelPath = "/test/model/path",
      inputDevice = "test-input-device",
      subtitleStyles = "-fx-font-size: 24;\n-fx-font-style: bold;",
      subtitleCount = 8,
      bounds = SubtitlerConfig.WindowBounds(27, 43, 856, 623)
    )
    val configFile = newTempFile()
    System.setProperty(SubtitlerConfig.ConfigFileProperty, configFile.toString)
    SubtitlerConfig.saveConfig(config)
    val controller = createController()

    val stage = mock[JfxWindow]
    controller.setUp(stage)

    controller.modelPath.value should be(config.modelPath)
    controller.selectedInputDevice.value should be(config.inputDevice)
    controller.subtitleStyles.value should be(config.subtitleStyles)
    controller.subtitleCount.value should be(config.subtitleCount)
    verify(stage).setX(config.bounds.x)
    verify(stage).setY(config.bounds.y)
    verify(stage).setWidth(config.bounds.width)
    verify(stage).setHeight(config.bounds.height)

  it should "persist the configuration settings on shutdown" in :
    val config = SubtitlerConfig(
      modelPath = "/test/model/path",
      inputDevice = "test-input-device",
      subtitleStyles = "-fx-font-size: 24;\n-fx-font-style: bold;",
      subtitleCount = 8,
      bounds = SubtitlerConfig.WindowBounds(49, 75, 523, 481)
    )
    val configFile = newTempFile()
    System.setProperty(SubtitlerConfig.ConfigFileProperty, configFile.toString)
    val actorSystem = mock[ActorSystem]
    when(actorSystem.terminate()).thenReturn(Future.successful(Terminated))
    val controller = createController(actorSystem = actorSystem)
    controller.modelPath.value = config.modelPath
    controller.selectedInputDevice.value = config.inputDevice
    controller.subtitleStyles.value = config.subtitleStyles
    controller.subtitleCount.value = config.subtitleCount
    val stage = mock[JfxWindow]
    when(stage.getX).thenReturn(config.bounds.x)
    when(stage.getY).thenReturn(config.bounds.y)
    when(stage.getWidth).thenReturn(config.bounds.width)
    when(stage.getHeight).thenReturn(config.bounds.height)
    
    controller.shutdown(stage)

    val savedConfig = SubtitlerConfig.loadConfig()
    savedConfig should be(config)
