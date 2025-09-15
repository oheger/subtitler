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

import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import scalafx.application.Platform

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger

/**
  * Test class for [[UiSynchronizer]].
  */
class UiSynchronizerSpec extends AnyFlatSpecLike with Matchers:
  "UiSynchronizer" should "run an action on the event thread" in :
    val invokeCount = new AtomicInteger
    val latch = new CountDownLatch(1)
    val sync = new UiSynchronizer

    Platform.startup(() => {})
    sync.runOnEventThread:
      invokeCount.incrementAndGet()
      Platform.isFxApplicationThread shouldBe true
      latch.countDown()

    latch.await(3, TimeUnit.SECONDS) shouldBe true
    invokeCount.get() should be(1)
