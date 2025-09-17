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

import com.github.oheger.subtitler.stream.SpeechRecognizerStream
import javafx.collections.{FXCollections, ObservableList}
import org.apache.pekko.Done
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Sink
import scalafx.beans.binding.Bindings
import scalafx.beans.property.{IntegerProperty, ObjectProperty, StringProperty}
import scalafx.stage.{DirectoryChooser, Window}

import java.io.File
import javax.sound.sampled.AudioSystem
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration

object Controller:
  /** The default number of subtitles that are displayed. */
  final val DefaultSubtitleCount = 3
end Controller

/**
  * A class acting a controller for the UI of this application.
  *
  * This class holds the current application state and makes sure - via binding
  * of properties - that the UI displays it correctly.
  *
  * @param actorSystem  the actor system
  * @param synchronizer the object to sync with the event thread
  * @param streamRunner the object to start a speech recognition stream
  */
class Controller(actorSystem: ActorSystem = ActorSystem("Subtitler"),
                 synchronizer: UiSynchronizer = new UiSynchronizer,
                 streamRunner: SpeechRecognizerStream.Runner = SpeechRecognizerStream.run):

  import Controller.*

  /**
    * A property that stores the Audio mixers that are currently available.
    * This is used to populate the combo box for selecting the audio input.
    */
  final val inputDevices: ObjectProperty[ObservableList[String]] = ObjectProperty(FXCollections.observableArrayList())

  /**
    * A property that stores the selected element of the combo box with the
    * input device names.
    */
  final val selectedInputDevice: StringProperty = StringProperty("")

  /**
    * A property that stores the path to the speech model to be used for
    * speech recognition.
    */
  final val modelPath: StringProperty = StringProperty("")

  /**
    * A property that stores the number of subtitles to be displayed. When new
    * text is recognized, the oldest subtitle is removed, and a new is added to
    * keep this number.
    */
  final val subtitleCount = IntegerProperty(DefaultSubtitleCount)

  /**
    * Stores the handle to a currently running speech recognition stream.
    */
  private val streamHandle: ObjectProperty[Option[SpeechRecognizerStream.StreamHandle[Done]]] = ObjectProperty(None)

  /**
    * A property that holds a flag whether all conditions are fulfilled to
    * start a speech recognition stream for generating subtitles. This property
    * can be bound to the ''enabled'' property of the start button.
    */
  final val canStartRecognizerStream =
    Bindings.createBooleanBinding(
      func = () => !Option(selectedInputDevice.value).forall(_.isBlank) &&
        !Option(modelPath.value).forall(_.isBlank) &&
        streamHandle.value.isEmpty,
      dependencies = selectedInputDevice, modelPath, streamHandle
    )

  /**
    * A property with a list that contains the currently available subtitles.
    * This can be bound to the UI that displays subtitles.
    */
  final val subtitles = ObjectProperty(FXCollections.observableArrayList[String]())

  /** The execution context to use for operations on futures. */
  private given ExecutionContext = actorSystem.dispatcher

  /**
    * Initializes this controller. This function must be called when the
    * application starts up. It makes sure that the controller's properties are
    * correctly populated with their initial values.
    */
  def setUp(): Unit =
    updateInputDevices()

  /**
    * Performs cleanup of resources when shutting down the application. This
    * function should be called when the application is closing.
    */
  def shutdown(): Unit =
    Await.ready(actorSystem.terminate(), Duration.Inf)

  /**
    * Updates the property with information about available mixers.
    */
  def updateInputDevices(): Unit =
    inputDevices.value.clear()
    AudioSystem.getMixerInfo.sortWith(_.getName < _.getName).foreach: info =>
      inputDevices.value.add(info.getName)

  /**
    * Shows a dialog to select the directory of the speech model. If this
    * dialog is executed successfully, the ''modelPath'' property is updated.
    *
    * @param parent the parent window for the dialog
    */
  def chooseModelPath(parent: Window): Unit =
    val chooser = new DirectoryChooser
    Option(modelPath.value).filterNot(_.isBlank).foreach: path =>
      chooser.initialDirectory = new File(path)
    modelPath.value = chooser.showDialog(parent).getAbsolutePath

  /**
    * Starts a stream to recognize speech and generate subtitles with the 
    * current configuration settings if all criteria are met. Result is 
    * '''false''' if the stream cannot be started.
    *
    * @return a flag indicating whether the operation was successful
    */
  def startRecognizerStream(): Boolean =
    if canStartRecognizerStream.value then
      subtitles.value.clear()
      val handle = streamRunner(selectedInputDevice.value, modelPath.value, recognizerStreamSink)(using actorSystem)
      handleCompletedStream(handle)
      streamHandle.value = Some(handle)
      true
    else
      false

  /**
    * Stops a currently running stream to recognize speech. The return value
    * indicates whether this was successful; '''false''' means that no stream
    * is currently running.
    *
    * @return a flag indicating whether the operation was successful
    */
  def stopRecognizerStream(): Boolean =
    streamHandle.value match
      case Some(handle) =>
        handle.cancel()
        true
      case None =>
        false

  /**
    * Returns the [[Sink]] for the speech recognizer stream. This sink stores
    * the recognized texts, making sure that processing happens on the event
    * dispatch thread.
    *
    * @return the [[Sink]] for the recognizer stream
    */
  private def recognizerStreamSink: Sink[String, Future[Done]] =
    Sink.foreach[String]: text =>
      synchronizer.runOnEventThread:
        val itemsCount = subtitles.value.size()
        val items = if itemsCount < subtitleCount.value then
          subtitles.value
        else
          subtitles.value.subList(itemsCount - subtitleCount.value + 1, itemsCount)

        val newItems = FXCollections.observableArrayList[String]()
        newItems.addAll(items)
        newItems.add(text)
        subtitles.value = newItems

  /**
    * Installs a handler to update the application state when the recognizer
    * stream represented by the given handle completes.
    *
    * @param handle the handle to the stream
    */
  private def handleCompletedStream(handle: SpeechRecognizerStream.StreamHandle[Done]): Unit =
    handle.materializedValue.foreach: _ =>
      synchronizer.runOnEventThread:
        streamHandle.value = None
