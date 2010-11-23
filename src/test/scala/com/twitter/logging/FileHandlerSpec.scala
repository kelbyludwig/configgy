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

package com.twitter
package logging

import java.io._
import java.util.{logging => javalog}
import org.specs.Specification
import extensions._

class FileHandlerSpec extends Specification with TempFolder {
  "FileHandler" should {
    val record1 = new javalog.LogRecord(Level.INFO, "first post!")

    "honor append setting on logfiles" in {
      withTempFolder {
        val f = new OutputStreamWriter(new FileOutputStream(folderName + "/test.log"), "UTF-8")
        f.write("hello!\n")
        f.close

        val config = new FileHandlerConfig {
          val filename = folderName + "/test.log"
          override val formatter = BareFormatter
          val policy = Policy.Hourly
          val append = true
        }
        val handler = new FileHandler(config)
        handler.publish(record1)

        val f2 = new BufferedReader(new InputStreamReader(new FileInputStream(folderName +
          "/test.log")))
        f2.readLine mustEqual "hello!"
      }

      withTempFolder {
        val f = new OutputStreamWriter(new FileOutputStream(folderName + "/test.log"), "UTF-8")
        f.write("hello!\n")
        f.close

        val config = new FileHandlerConfig {
          val filename = folderName + "/test.log"
          override val formatter = BareFormatter
          val policy = Policy.Hourly
          val append = false
        }
        val handler = new FileHandler(config)

        handler.publish(record1)

        val f2 = new BufferedReader(new InputStreamReader(new FileInputStream(folderName +
          "/test.log")))
        f2.readLine mustEqual "first post!"
      }
    }
  }
}
