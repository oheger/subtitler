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

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.ActorAttributes
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.testkit.TestKit
import org.apache.pekko.util.ByteString
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.mockito.{ArgumentCaptor, MockedStatic}
import org.scalatest.flatspec.AsyncFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, OptionValues}
import org.scalatestplus.mockito.MockitoSugar

import java.io.ByteArrayInputStream
import javax.sound.sampled.{AudioSystem, DataLine, Mixer, TargetDataLine}
import scala.compiletime.uninitialized
import scala.util.Random

object CaptureAudioSourceSpec:
  /** The name of the mixer used by test cases. */
  private val TestMixerName = "theTestMixer"

/**
  * Test class for [[CaptureAudioSource]].
  */
class CaptureAudioSourceSpec(testSystem: ActorSystem) extends TestKit(testSystem) with AsyncFlatSpecLike
  with BeforeAndAfterAll with BeforeAndAfterEach with Matchers with MockitoSugar with OptionValues:
  def this() = this(ActorSystem("CaptureAudioSourceSpec"))

  import CaptureAudioSourceSpec.*

  override protected def afterAll(): Unit =
    TestKit.shutdownActorSystem(system)
    super.afterAll()

  /** The static mock for the Java Audio System. */
  private var mockedAudioSystem: MockedStatic[AudioSystem] = uninitialized

  override protected def beforeEach(): Unit =
    super.beforeEach()
    mockedAudioSystem = mockStatic(classOf[AudioSystem])

  override protected def afterEach(): Unit =
    mockedAudioSystem.close()
    super.afterEach()

  /**
    * Prepares the mocked audio system to return mixer information with the
    * given names.
    *
    * @param mixerNames the names of existing mixers
    * @return the array with mixer information used by the audio system
    */
  private def mockMixers(mixerNames: Iterable[String]): Array[Mixer.Info] =
    val infos = mixerNames.map(createMixerInfo).toArray
    mockedAudioSystem.when(() => AudioSystem.getMixerInfo).thenReturn(infos)
    infos

  /**
    * Creates a mock [[Mixer.Info]] object with the given name.
    *
    * @param name the name of the mixer
    * @return the mock for this [[Mixer.Info]] object
    */
  private def createMixerInfo(name: String): Mixer.Info =
    val infoMock = mock[Mixer.Info]
    when(infoMock.getName).thenReturn(name)
    infoMock

  "CaptureAudioSource" should "return a source that captures audio from the selected mixer" in :
    val mixer = mock[Mixer]
    val targetLine = mock[TargetDataLine]
    val mixerInfo = mockMixers(List("test", "foo", TestMixerName, "bar"))(2)
    mockedAudioSystem.when(() => AudioSystem.getMixer(mixerInfo)).thenReturn(mixer)
    when(mixer.getLine(any())).thenReturn(targetLine)
    when(targetLine.getBufferSize).thenReturn(8192)

    val audioData = Random.nextBytes(65536)
    val audioStream = new ByteArrayInputStream(audioData)
    when(targetLine.read(any(), any(), any())).thenAnswer((invocation: InvocationOnMock) =>
      val buf = invocation.getArgument(0, classOf[Array[Byte]])
      val off = invocation.getArgument(1, classOf[Integer])
      val len = invocation.getArgument(2, classOf[Integer])
      audioStream.read(buf, off, len))

    CaptureAudioSource.factory(TestMixerName) flatMap : source =>
      verify(targetLine).open(CaptureAudioSource.CaptureFormat)
      verify(targetLine).start()
      val captLineInfo = ArgumentCaptor.forClass(classOf[DataLine.Info])
      verify(mixer).getLine(captLineInfo.capture())
      captLineInfo.getValue.getLineClass should be(classOf[TargetDataLine])
      captLineInfo.getValue.getFormats should contain only CaptureAudioSource.CaptureFormat
      val dispatcherAttr = source.getAttributes.get[ActorAttributes.Dispatcher]
      dispatcherAttr.value.dispatcher should be(CaptureAudioSource.BlockingDispatcherName)

      val sink = Sink.fold[ByteString, ByteString](ByteString.empty)(_ ++ _)
      source.runWith(sink) map : result =>
        verify(targetLine).stop()
        verify(targetLine).close()
        result should be(ByteString(audioData))

  it should "return a source that fails the stream if the specified mixer cannot be resolved" in :
    mockMixers(List("not", "the", "expected", "mixer"))

    CaptureAudioSource.factory(TestMixerName) flatMap : source =>
      val futEx = recoverToExceptionIf[IllegalStateException]:
        val sink = Sink.fold[ByteString, ByteString](ByteString.empty)(_ ++ _)
        source.runWith(sink)

      futEx map : exception =>
        exception.getMessage should include(TestMixerName)
