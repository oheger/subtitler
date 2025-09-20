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

package com.github.oheger.subtitler.config

import com.github.oheger.subtitler.TempFileSupport
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import java.nio.file.{Files, Path}

/**
  * Test class for [[SubtitlerConfig]].
  */
class SubtitlerConfigSpec extends AnyFlatSpecLike with BeforeAndAfterEach with Matchers with TempFileSupport:
  override protected def afterEach(): Unit =
    System.clearProperty(SubtitlerConfig.ConfigFileProperty)
    super.afterEach()

  /**
    * Sets the system property for the application configuration file to the
    * given path.
    *
    * @param configPath the path to the configuration file
    */
  private def initConfigFilePath(configPath: String): Unit =
    System.setProperty(SubtitlerConfig.ConfigFileProperty, configPath)

  /**
    * Sets the system property for the application configuration file to the
    * given [[Path]].
    *
    * @param configPath the path to the configuration file
    */
  private def initConfigFilePath(configPath: Path): Unit =
    initConfigFilePath(configPath.toString)

  "configFilePath" should "return the default path if no property is set" in :
    val path = SubtitlerConfig.configFilePath

    val directory = path.getParent.toString
    directory should be(System.getProperty("user.home"))
    val file = path.getFileName.toString
    file should be(SubtitlerConfig.DefaultConfigFileName)

  it should "evaluate the configFile property" in :
    val ConfigFilePath = "/home/test/conf/.subtitler/config.json"
    initConfigFilePath(ConfigFilePath)

    SubtitlerConfig.configFilePath.toString should be(ConfigFilePath)

  "loadConfig" should "return the default configuration if no configuration file exists" in :
    initConfigFilePath("/a/non/existing/path")

    SubtitlerConfig.loadConfig() should be(SubtitlerConfig.DefaultConfig)

  it should "return the default configuration if the configuration file cannot be loaded" in :
    val configFile = newTempFile()
    Files.writeString(configFile, "This is not a valid JSON configuration file.")
    initConfigFilePath(configFile)

    SubtitlerConfig.loadConfig() should be(SubtitlerConfig.DefaultConfig)

  it should "load an existing configuration file" in :
    val configFile = newTempFile()
    initConfigFilePath(configFile)
    val config = SubtitlerConfig(
      modelPath = "/opt/vosc/my-model",
      inputDevice = "my-audio-device",
      subtitleCount = 5,
      subtitleStyles = "-fx-font-size: 22;\n-fx-font-color:black;"
    )

    SubtitlerConfig.saveConfig(config) should be(configFile)
    val loadedConfig = SubtitlerConfig.loadConfig()

    loadedConfig should be(config)

  it should "load an existing configuration file with missing properties" in :
    val configFile = newTempFile()
    Files.writeString(configFile, "{}")
    initConfigFilePath(configFile)

    SubtitlerConfig.loadConfig() should be(SubtitlerConfig.DefaultConfig)

  "saveConfig" should "not store undefined properties" in :
    val configFile = newTempFile()
    initConfigFilePath(configFile)
    val config = SubtitlerConfig(
      modelPath = "",
      inputDevice = "",
      subtitleStyles = "",
      subtitleCount = 3
    )

    SubtitlerConfig.saveConfig(config)

    val configStr = Files.readString(configFile)
    configStr should not include "modelPath"
    configStr should not include "inputDevice"
    configStr should not include "subtitleStyles"
    configStr should include("subtitleCount")
