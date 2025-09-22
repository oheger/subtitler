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

import javafx.event.EventHandler
import scalafx.application.JFXApp3
import scalafx.beans.binding.Bindings
import scalafx.geometry.Insets
import scalafx.geometry.Pos.CenterLeft
import scalafx.scene.Scene
import scalafx.scene.control.*
import scalafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory
import scalafx.scene.layout.{BorderPane, HBox, StackPane, VBox}
import scalafx.scene.paint.Color
import scalafx.scene.shape.Circle

import scala.compiletime.uninitialized

/**
  * The main class of the ''subtitler'' application. It creates the main
  * window.
  */
object UiMain extends JFXApp3:
  /** The object controlling the application state. */
  private var controller: Controller = uninitialized

  override def start(): Unit =
    controller = new Controller

    stage = new JFXApp3.PrimaryStage:
      title = "Subtitler"
      width = 640
      height = 480
      scene = new Scene:
        root = new StackPane:
          padding = Insets(20, 20, 20, 20)
          children = Seq(
            configPane,
            subtitlesPane,
            errorPane
          )
    controller.setUp()

  override def stopApp(): Unit =
    controller.shutdown()
    super.stopApp()

  /**
    * Returns the pane for the configuration settings.
    *
    * @return the configuration pane
    */
  private def configPane: TitledPane =
    new TitledPane:
      visible <== controller.configViewVisible
      text = "Configuration"
      collapsible = false
      content = new VBox:
        padding = Insets(20, 20, 20, 20)
        spacing = 5
        children = Seq(
          new Label:
            text = "Model path:",
          new HBox:
            spacing = 20
            alignment = CenterLeft
            children = Seq(
              new TextField:
                prefWidth = 300
                text <==> controller.modelPath,
              new Button:
                text = "Select..."
                onAction.value = _ => controller.chooseModelPath(stage)
            ),
          new Label:
            text = "Input device:",
          new HBox:
            spacing = 20
            alignment = CenterLeft
            children = Seq(
              new ComboBox[String]:
                items <==> controller.inputDevices
                value <==> controller.selectedInputDevice,
              new Button:
                text = "Reload"
                onAction.value = _ => controller.updateInputDevices()
            ),
          new Label:
            text = "Number of subtitle rows:",
          createSubtitleCountSpinner(),
          new Label:
            text = "Styles for subtitles:",
          new TextArea:
            text <==> controller.subtitleStyles,
          new Button:
            margin = Insets(top = 20, right = 0, bottom = 0, left = 0)
            text = "Start subtitles"
            padding = Insets(20, 20, 20, 20)
            graphic = Circle(radius = 10, fill = Color(red = 0.8, green = 0, blue = 0, opacity = 1))
            disable <== !controller.canStartRecognizerStream
            onAction.value = _ => controller.startRecognizerStream()
        )

  /**
    * Creates the ''spinner'' controller for entering the number of subtitles
    * to be displayed.
    *
    * @return the spinner for the number of subtitles
    */
  private def createSubtitleCountSpinner(): Spinner[Integer] =
    val valueFactory = new IntegerSpinnerValueFactory(1, 100)
    valueFactory.value <==> controller.subtitleCount
    new Spinner[Integer](valueFactory)

  /**
    * Returns the pane showing the subtitles.
    *
    * @return the subtitles pane
    */
  private def subtitlesPane: BorderPane =
    import scala.jdk.CollectionConverters.*
    val subtitlesText = Bindings.createStringBinding(
      func = () => controller.subtitles.value.asScala.mkString(
        start = "",
        end = "",
        sep = "\n"
      ),
      dependencies = controller.subtitles
    )
    new BorderPane:
      visible <== controller.subtitleViewVisible
      center = new TextArea:
        text <== subtitlesText
        wrapText = true
        editable = false
        style <== controller.subtitleStyles
      bottom = new HBox:
        margin = Insets(top = 10, left = 10, right = 10, bottom = 10)
        children = Seq(
          new Button:
            text = "Stop"
            padding = Insets(20, 20, 20, 20)
            graphic = Circle(radius = 10, fill = Color(red = 1, green = 1, blue = 1, opacity = 1))
            onAction.value = _ => controller.stopRecognizerStream()
        )

  /**
    * Returns the pane with error information in case the recognizer stream
    * failed with an error.
    *
    * @return the error pane
    */
  private def errorPane: TitledPane =
    new TitledPane:
      visible <== controller.errorViewVisible
      text <== controller.exceptionClass
      collapsible = false
      content = new BorderPane:
        center = new TextArea:
          text <== controller.exceptionMessage
          margin = Insets(top = 10, right = 0, bottom = 0, left = 0)
          wrapText = true
          editable = false
          style =
            """
              |-fx-text-fill: red;
              |-fx-font-size: 14;
              |""".stripMargin
        bottom = new HBox:
          margin = Insets(top = 10, left = 10, right = 10, bottom = 10)
          children = Seq(
            new Button:
              text = "Back"
              padding = Insets(20, 20, 20, 20)
              onAction.value = _ => controller.resetError()
          )
