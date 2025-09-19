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

import com.github.oheger.subtitler.stream.{CaptureAudioSource, SpeechRecognizerStage, SpeechRecognizerStream}
import com.github.oheger.subtitler.ui.ControllerStreamSpec.{ErrorPrefix, IOExceptionPrefix, IllegalArgumentExceptionPrefix, InputDevice, ModelPath, generateException}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.BoundedSourceQueue
import org.apache.pekko.stream.scaladsl.{Keep, Sink, Source}
import org.apache.pekko.testkit.TestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import java.io.IOException
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}
import scala.annotation.tailrec
import scala.concurrent.Future

object ControllerStreamSpec:
  /** The model path used by the tests. */
  private val ModelPath = "/path/to/the/test/speech/model"

  /** The name of the input device for audio capturing. */
  private val InputDevice = "My-test-input-device"

  /**
    * A prefix for a stream element that causes the stream to fail with a
    * default exception. This is used to test exception handling.
    */
  private val ErrorPrefix = "error:"

  /**
    * A prefix for a stream element that causes the stream to fail with an IO
    * exception.
    */
  private val IOExceptionPrefix = "io-error:"

  /**
    * A prefix for a stream element that the stream to fail with an illegal
    * argument exception.
    */
  private val IllegalArgumentExceptionPrefix = "illegal-error:"

  /**
    * Checks whether the given text indicates that an exception should be
    * thrown. If so, a corresponding exception is generated using an optional
    * type property.
    *
    * @param text the text flowing through the stream
    * @return an [[Option]] with the exception to throw
    */
  private def generateException(text: String): Option[Throwable] =
    val posPrefix = text.indexOf(':')
    if posPrefix < 0 then
      None
    else
      val message = text.substring(posPrefix + 1)
      val exception = text.substring(0, posPrefix + 1) match
        case IOExceptionPrefix => new IOException(message)
        case IllegalArgumentExceptionPrefix => new IllegalArgumentException(message)
        case _ => new IllegalStateException(message)
      Some(exception)
end ControllerStreamSpec

/**
  * A special test class for [[Controller]] that tests the handling of the
  * speech recognition stream. Since populating the sink of the stream requires
  * a real actor system, a dedicated test class is used.
  */
class ControllerStreamSpec(testSystem: ActorSystem) extends TestKit(testSystem) with AnyFlatSpecLike
  with BeforeAndAfterAll with Matchers:
  def this() = this(ActorSystem("ControllerStreamSpec"))

  override protected def afterAll(): Unit =
    TestKit.shutdownActorSystem(system)
    super.afterAll()

  "A Controller" should "process results from the recognition stream" in :
    val textResults = (1 to Controller.DefaultSubtitleCount).map(idx => s"Text $idx")
    val helper = new StreamTestHelper().startStream()

    textResults.foreach(helper.pushResult)
    helper.syncActions(textResults.length)

    helper.controller.subtitles.value should contain theSameElementsInOrderAs textResults
    helper.completeStream()

  it should "only keep the configured number of subtitles" in :
    val SubtitleCount = 5
    val textResults = (1 to (SubtitleCount + 1)).map(idx => s"This is subtitle $idx.")
    val helper = new StreamTestHelper().startStream()
    helper.controller.subtitleCount.value = SubtitleCount

    textResults.foreach(helper.pushResult)
    helper.syncActions(textResults.length)

    helper.controller.subtitles.value should contain theSameElementsInOrderAs textResults.drop(1)
    helper.completeStream()

  it should "reset the subtitles when starting the stream" in :
    val Subtitle = "The new subtitle"
    val helper = new StreamTestHelper
    helper.controller.subtitles.value.addAll("foo", "bar", "baz")

    helper.startStream()
      .pushResult(Subtitle)
      .syncActions(1)

    helper.controller.subtitles.value should contain only Subtitle
    helper.completeStream()

  it should "allow starting another stream after completing one" in :
    val helper = new StreamTestHelper

    helper.startStream()
      .completeStream()
      .syncActions(1)

    helper.controller.canStartRecognizerStream.value shouldBe true

  it should "stop a currently running stream" in :
    val helper = new StreamTestHelper
    helper.startStream()

    helper.controller.stopRecognizerStream() shouldBe true

    helper.streamStopCount should be(1)
    helper.completeStream()

  it should "ignore a stop operation if no stream is running" in :
    val helper = new StreamTestHelper

    helper.controller.stopRecognizerStream() shouldBe false

  it should "record the exception if the stream fails" in :
    val ErrorMessage = "Error during speech recognition."
    val helper = new StreamTestHelper

    helper.startStream()
      .pushResult("some text")
      .pushResult(ErrorPrefix + ErrorMessage)
      .syncActions(2)

    helper.controller.exceptionMessage.value should be(ErrorMessage)
    helper.controller.exceptionClass.value should be("IllegalStateException")

  it should "add error information if the model could not be loaded" in :
    val ErrorMessage = "Failed to create model"
    val helper = new StreamTestHelper

    helper.startStream()
      .pushResult(IOExceptionPrefix + ErrorMessage)
      .syncActions(1)

    helper.controller.exceptionClass.value should be("IOException")
    helper.controller.exceptionMessage.value should startWith(ErrorMessage)
    helper.controller.exceptionMessage.value should include("'Model path' property")

  it should "add error information if the line could not be opened" in :
    val ErrorMessage = "Line unsupported: interface TargetDataLine supporting format..."
    val helper = new StreamTestHelper

    helper.startStream()
      .pushResult(IllegalArgumentExceptionPrefix + ErrorMessage)
      .syncActions(1)

    helper.controller.exceptionClass.value should be("IllegalArgumentException")
    helper.controller.exceptionMessage.value should startWith(ErrorMessage)
    helper.controller.exceptionMessage.value should include("Please choose a different input device")

  it should "allow resetting error information" in :
    val helper = new StreamTestHelper
    import helper.controller.*
    exceptionMessage.value = "Some exception message."
    exceptionClass.value = "SomeException"

    resetError()

    exceptionMessage.value should be("")
    exceptionClass.value should be("")

  it should "show the config view initially" in :
    val helper = new StreamTestHelper

    helper.checkVisibilityFlags(configViewVisible = true)

  it should "show the subtitles view if a stream is running" in :
    val helper = new StreamTestHelper

    helper.startStream()
      .checkVisibilityFlags(subtitleViewVisible = true)
      .completeStream()

  it should "show the error view if the stream has failed" in :
    val helper = new StreamTestHelper

    helper.startStream()
      .pushResult(ErrorPrefix + "Some error")
      .syncActions(1)
      .checkVisibilityFlags(errorViewVisible = true)

  it should "show the config view after a stream completes" in :
    val helper = new StreamTestHelper

    helper.startStream()
      .pushResult("some subtitle")
      .completeStream()
      .syncActions(2)
      .checkVisibilityFlags(configViewVisible = true)

  it should "show the config view after resetting an error" in :
    val helper = new StreamTestHelper

    helper.startStream()
      .pushResult(ErrorPrefix + "Something went wrong.")
      .syncActions(1)
    helper.controller.resetError()

    helper.checkVisibilityFlags(configViewVisible = true)

  /**
    * A test helper class managing a controller and its dependencies.
    */
  private class StreamTestHelper:
    /** A queue to fetch actions from the synchronizer. */
    private val syncActionQueue = new LinkedBlockingQueue[() => Unit]

    /** Stores the queue for pushing data to the stream. */
    private val refSourceQueue = new AtomicReference[BoundedSourceQueue[String]]

    /** A counter to record operations to stop a stream. */
    private val streamStoppedCounter = new AtomicInteger

    /** The controller under test. */
    final val controller = createController()

    /**
      * Starts the recognizer stream.
      *
      * @return this test helper
      */
    def startStream(): StreamTestHelper =
      controller.startRecognizerStream() shouldBe true
      this

    /**
      * Pushes the given text as a result of speech recognition to the test
      * stream.
      *
      * @param text the text to be pushed
      * @return this test helper
      */
    def pushResult(text: String): StreamTestHelper =
      sourceQueue.offer(text)
      this

    /**
      * Completes the speech recognition stream.
      *
      * @return this test helper
      */
    def completeStream(): StreamTestHelper =
      sourceQueue.complete()
      this

    /**
      * Processes the given number of sync actions passed to the stub UI
      * synchronizer.
      *
      * @param count the number of actions to process
      * @return this test helper
      */
    @tailrec final def syncActions(count: Int): StreamTestHelper =
      if count <= 0 then
        this
      else
        val action = syncActionQueue.poll(3, TimeUnit.SECONDS)
        action should not be null
        action()
        syncActions(count - 1)

    /**
      * Returns the number of stop operations on recognizer streams.
      *
      * @return the number of stream stop operations
      */
    def streamStopCount: Int = streamStoppedCounter.get()

    /**
      * Returns the queue for pushing text results into the stream.
      *
      * @return the source queue
      */
    private def sourceQueue: BoundedSourceQueue[String] =
      awaitCond(refSourceQueue.get() != null)
      refSourceQueue.get()

    /**
      * Checks the properties controlling the visibility of the different views
      * against the expected values.
      *
      * @param configViewVisible   flag for the config view
      * @param subtitleViewVisible flag for the subtitles view
      * @param errorViewVisible    flag for the error view
      * @return this test helper
      */
    def checkVisibilityFlags(configViewVisible: Boolean = false,
                             subtitleViewVisible: Boolean = false,
                             errorViewVisible: Boolean = false): StreamTestHelper =
      controller.configViewVisible.value shouldBe configViewVisible
      controller.subtitleViewVisible.value shouldBe subtitleViewVisible
      controller.errorViewVisible.value shouldBe errorViewVisible
      this

    /**
      * Creates a [[UiSynchronizer]] to be used for tests. This implementation
      * puts action to sync with the UI thread in a queue from where they can
      * be obtained from another thread.
      *
      * @return the synchronizer
      */
    private def createSynchronizer(): UiSynchronizer =
      new UiSynchronizer:
        override def runOnEventThread(action: => Unit): Unit =
          syncActionQueue.offer(() => action)

    /**
      * Creates the runner to start the test stream. The source of the stream
      * is using a queue for pushing single subtitles into the stream.
      * Subtitles starting with an error prefix cause the stream to fail.
      *
      * @return the runner
      */
    private def createStreamRunner(): SpeechRecognizerStream.Runner =
      new SpeechRecognizerStream.Runner:
        override def apply[MAT](mixerName: String,
                                modelPath: String,
                                sink: Sink[String, Future[MAT]],
                                sourceFactory: CaptureAudioSource.Factory,
                                recognizerFactory: SpeechRecognizerStage.Factory)
                               (using system: ActorSystem): SpeechRecognizerStream.StreamHandle[MAT] =
          mixerName should be(InputDevice)
          modelPath should be(ModelPath)
          val source = Source.queue[String](8)
            .map: txt =>
              generateException(txt) match
                case Some(exception) => throw exception
                case None => txt
          val graph = source.toMat(sink)(Keep.both)
          val (queue, futStream) = graph.run()
          refSourceQueue.set(queue)

          new SpeechRecognizerStream.StreamHandle[MAT]:
            override def materializedValue: Future[MAT] = futStream

            override def cancel(): Unit =
              streamStoppedCounter.incrementAndGet()

    /**
      * Creates the controller to be tested.
      *
      * @return the controller
      */
    private def createController(): Controller =
      val ctrl = new Controller(
        actorSystem = system,
        synchronizer = createSynchronizer(),
        streamRunner = createStreamRunner()
      )
      ctrl.modelPath.value = ModelPath
      ctrl.selectedInputDevice.value = InputDevice
      ctrl