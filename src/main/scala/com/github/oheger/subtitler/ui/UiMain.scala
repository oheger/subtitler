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
import scalafx.geometry.Insets
import scalafx.geometry.Pos.Center
import scalafx.scene.Scene
import scalafx.scene.control.{Button, ComboBox, Label, TextField, TitledPane}
import scalafx.scene.layout.{BorderPane, HBox, VBox}
import scalafx.scene.paint.Color
import scalafx.scene.shape.Circle

/**
  * The main class of the ''subtitler'' application. It creates the main
  * window.
  */
object UiMain extends JFXApp3:
  override def start(): Unit =
    stage = new JFXApp3.PrimaryStage:
      title = "Subtitler"
      width = 640
      height = 480
      scene = new Scene:
        content = new BorderPane:
          padding = Insets(20, 20, 20, 20)
          center = new TitledPane:
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
                  alignment = Center
                  children = Seq(
                    new TextField:
                      prefWidth = 200,
                    new Button:
                      text = "Select..."
                      onAction.value = _ => println("Button 'Select' clicked.")
                  ),
                new Label:
                  text = "Input device:",
                new ComboBox[String](items = Seq("Mixer1", "Mixer2", "Mixer3")),
                new Button:
                  margin = Insets(top = 20, right = 0, bottom = 0, left = 0)
                  text = "Start subtitles"
                  padding = Insets(20, 20, 20, 20)
                  graphic = Circle(radius = 10, fill = Color(red = 0.8, green = 0, blue = 0, opacity = 1))
          )
