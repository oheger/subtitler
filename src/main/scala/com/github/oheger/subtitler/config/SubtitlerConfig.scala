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

import org.apache.logging.log4j.LogManager
import spray.json.*
import spray.json.DefaultJsonProtocol.*

import java.nio.file.{Files, Path, Paths}
import scala.util.{Failure, Success, Try}

object SubtitlerConfig extends DefaultJsonProtocol:
  /**
    * The name of a system property that can be specified to adapt the path to
    * the configuration file. If this property is not set, the default file
    * name in the user's home directory is used.
    */
  final val ConfigFileProperty = "configFile"

  /**
    * The default name of the configuration file. This file (in the user's home
    * directory) is used if the ''ConfigFileProperty'' is unspecified.
    */
  final val DefaultConfigFileName = ".subtitler.json"

  /**
    * A configuration with default values for all properties. This instance is
    * used if no persistent configuration could be found.
    */
  final val DefaultConfig: SubtitlerConfig = SubtitlerConfig(
    modelPath = "",
    inputDevice = "",
    subtitleStyles = "",
    subtitleCount = 1
  )

  /**
    * A data class representing the configuration to be serialized to a JSON
    * file. Here, all properties are optional, as they might not be defined in
    * the configuration file.
    *
    * @param modelPath      the path to the Vosc model
    * @param inputDevice    the name of the device to capture audio from
    * @param subtitleStyles CSS styles for the view of the subtitles
    * @param subtitleCount  the number of subtitles to display
    */
  private case class SerializableConfig(modelPath: Option[String],
                                        inputDevice: Option[String],
                                        subtitleStyles: Option[String],
                                        subtitleCount: Option[Int])

  /** The format to serialize the configuration. */
  private given configFormat: RootJsonFormat[SerializableConfig] = jsonFormat4(SerializableConfig.apply)

  /** The logger. */
  private val log = LogManager.getLogger(classOf[SubtitlerConfig])

  /**
    * Returns the [[Path]] to the configuration file for this application.
    * Checks whether the system property is set which overrides the default
    * path.
    *
    * @return the [[Path]] to the configuration file
    */
  def configFilePath: Path =
    Option(System.getProperty(ConfigFileProperty))
      .map(p => Paths.get(p))
      .getOrElse(Paths.get(System.getProperty("user.home"), DefaultConfigFileName))

  /**
    * Loads the application configuration. Calls
    * [[SubtitlerConfig.configFilePath]] to determine the path to the file. If
    * this path points to an existing file, the file is loaded and converted to
    * a [[SubtitlerConfig]] object. If the path does not point to an existing
    * file or the deserialization of the file fails, the function returns the
    * default configuration.
    *
    * @return the application configuration
    */
  def loadConfig(): SubtitlerConfig =
    val path = configFilePath
    if Files.isRegularFile(path) then
      log.info("Loading configuration from file '{}'.", path)
      readConfigFile(path) match
        case Success(config) =>
          toSubtitlerConfig(config)
        case Failure(exception) =>
          log.error("Could not load configuration file. Using default configuration instead.", exception)
          DefaultConfig
    else
      log.info("Configuration file does not exist. Using default configuration instead.")
      DefaultConfig

  /**
    * Saves the given configuration to the path returned by
    * [[SubtitlerConfig.configFilePath]]. This function should be called when
    * the application shuts down, so that the configuration can be restored
    * when the application is started again.
    *
    * @param config the configuration to store
    * @return the path where the file was written
    */
  def saveConfig(config: SubtitlerConfig): Path =
    val json = toSerializableConfig(config).toJson
    val path = configFilePath
    log.info("Storing configuration to file '{}'.", path)
    Files.writeString(path, json.prettyPrint)

  /**
    * Tries to load the application configuration file and deserialize the
    * content to a configuration object.
    *
    * @param path the path to the file to load
    * @return a [[Try]] with the resulting configuration
    */
  private def readConfigFile(path: Path): Try[SerializableConfig] = Try:
    val content = Files.readString(path)
    val json = content.parseJson
    json.convertTo[SerializableConfig]

  /**
    * Converts the given [[SerializableConfig]] to a [[SubtitlerConfig]].
    *
    * @param config the serializable configuration
    * @return the application configuration
    */
  private def toSubtitlerConfig(config: SerializableConfig): SubtitlerConfig =
    SubtitlerConfig(
      modelPath = config.modelPath.getOrElse(DefaultConfig.modelPath),
      inputDevice = config.inputDevice.getOrElse(DefaultConfig.inputDevice),
      subtitleCount = config.subtitleCount.getOrElse(DefaultConfig.subtitleCount),
      subtitleStyles = config.subtitleStyles.getOrElse(DefaultConfig.subtitleStyles)
    )

  /**
    * Converts the given [[SubtitlerConfig]] to a [[SerializableConfig]]. Here
    * only the defined properties are taken into account.
    *
    * @param config the application configuration
    * @return the serializable configuration
    */
  private def toSerializableConfig(config: SubtitlerConfig): SerializableConfig =
    SerializableConfig(
      modelPath = toOptionString(config.modelPath),
      inputDevice = toOptionString(config.inputDevice),
      subtitleStyles = toOptionString(config.subtitleStyles),
      subtitleCount = Some(config.subtitleCount)
    )

  /**
    * Converts the given string to an [[Option]] that is undefined if the
    * string is empty.
    *
    * @param s the string
    * @return the resulting [[Option]]
    */
  private def toOptionString(s: String): Option[String] =
    Option(s).filterNot(_.isBlank)
end SubtitlerConfig

/**
  * A data class that represents configuration options for the subtitler
  * application. An instance is persisted, and is thus available when the
  * application is restarted.
  *
  * @param modelPath      the path to the Vosc model
  * @param inputDevice    the name of the device to capture audio from
  * @param subtitleStyles CSS styles for the view of the subtitles
  * @param subtitleCount  the number of subtitles to display
  */
case class SubtitlerConfig(modelPath: String,
                           inputDevice: String,
                           subtitleStyles: String,
                           subtitleCount: Int)
