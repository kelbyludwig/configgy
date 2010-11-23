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

import java.util.{logging => javalog}
import org.specs.Specification

class FormatterSpec extends Specification {
  val utcConfig = new FormatterConfig { override val timezone = Some("UTC") }

  "Formatter" should {
    "create a prefix" in {
      BasicFormatter.formatPrefix(Level.ERROR, "20080329-05:53:16.722", "(root)") mustEqual
        "ERR [20080329-05:53:16.722] (root): "
    }

    "format text" in {
      val record1 = new javalog.LogRecord(Level.ERROR, "error %s")
      BasicFormatter.formatText(record1) mustEqual "error %s"
      record1.setParameters(Array("123"))
      BasicFormatter.formatText(record1) mustEqual "error 123"
    }

    "format a timestamp" in {
      val formatter = new Formatter(utcConfig)
      val record1 = new javalog.LogRecord(Level.ERROR, "boo.")
      record1.setLoggerName("jobs")
      record1.setMillis(1206769996722L)
      formatter.format(record1) mustEqual "ERR [20080329-05:53:16.722] jobs: boo.\n"
    }
  }
}
