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

import scalafx.application.Platform

/**
  * A class to synchronize operations triggered from other threads with the
  * JavaFX event thread. An instance of this class is used to update the UI
  * after asynchronous operations have completed.
  */
class UiSynchronizer:
  /**
    * Executes the given action asynchronously on the JavaFX thread. Returns
    * immediately, not waiting for the completion of the action.
    *
    * @param action the action to be executed
    */
  def runOnEventThread(action: => Unit): Unit =
    Platform.runLater(action)
