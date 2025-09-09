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
package com.github.oheger.speech2text

import org.vosk.{Model, Recognizer}

import java.io.{BufferedInputStream, FileInputStream}
import javax.sound.sampled.AudioSystem
import scala.annotation.tailrec
import scala.util.Using

object Subtitler:
  def main(args: Array[String]): Unit =
    if args.length != 2 then
      println("Usage: subtitler <model> <input>")
      println("  <model>: path to the extracted model files")
      println("  <input>: path to the wav file to be processed.")
      System.exit(1)

    Using.Manager: use =>
      val model = use(new Model(args(0)))
      val audioIn = use(AudioSystem.getAudioInputStream(new BufferedInputStream(new FileInputStream(args(1)))))
      val recognizer = use(new Recognizer(model, audioIn.getFormat.getSampleRate))

      val format = audioIn.getFormat
      println("Audio format: " + format)
      val buf = new Array[Byte](4096)

      @tailrec def processInput(): Unit =
        val count = audioIn.read(buf)
        if count >= 0 then
          if recognizer.acceptWaveForm(buf, count) then
            println(s"[result]: ${recognizer.getResult}")
          else
            println(s"[partial]: ${recognizer.getPartialResult}")
          processInput()

      processInput()
      println(s"[final]: ${recognizer.getFinalResult}")
