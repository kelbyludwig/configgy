/*
 * Copyright 2010 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twitter.logging

import com.twitter.TempFolder
import org.specs.Specification
import config._

class ThrottledHandlerSpec extends Specification with TempFolder {
  private var handler: StringHandler = null

  "ThrottledHandler" should {
    doBefore {
      Logger.clearHandlers
      handler = new StringHandler(BareFormatter)
    }

    doAfter {
      Logger.clearHandlers
    }

    "throttle keyed log messages" in {
      val log = Logger()
      val config = new ThrottledHandlerConfig {
        val handler = ThrottledHandlerSpec.this.handler
        val maxToDisplay = 3
        val durationMilliseconds = 1000
      }
      val throttledLog = new ThrottledHandler(config)
      log.addHandler(throttledLog)

      log.error("apple: %s", "help!")
      log.error("apple: %s", "help 2!")
      log.error("orange: %s", "orange!")
      log.error("orange: %s", "orange!")
      log.error("apple: %s", "help 3!")
      log.error("apple: %s", "help 4!")
      log.error("apple: %s", "help 5!")
      throttledLog.reset()
      log.error("apple: %s", "done.")

      handler.get.split("\n").toList mustEqual List("apple: help!", "apple: help 2!", "orange: orange!", "orange: orange!", "apple: help 3!", "(swallowed 2 repeating messages)", "apple: done.")
    }
  }
}
