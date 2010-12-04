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

import java.net.InetSocketAddress
import java.util.{logging => javalog}
import com.twitter.extensions._
import com.twitter.TempFolder
import org.specs.Specification
import config._

class LoggerSpec extends Specification with TempFolder {
  private var myHandler: Handler = null
  private var log: Logger = null

  val timeFrozenFormatter = new FormatterConfig { timezone = "UTC" }.apply()
  val timeFrozenHandler = new StringHandler(timeFrozenFormatter) {
    override def publish(record: javalog.LogRecord) = {
      record.setMillis(1206769996722L)
      super.publish(record)
    }
  }

  private def parse(): List[String] = {
    val rv = myHandler.asInstanceOf[StringHandler].get.split("\n")
    myHandler.asInstanceOf[StringHandler].clear()
    rv.toList
  }

  "Logger" should {
    doBefore {
      Logger.clearHandlers
      timeFrozenHandler.clear()
      myHandler = new StringHandler(BareFormatter)
      log = Logger.get("")
      log.setLevel(Level.ERROR)
      log.addHandler(myHandler)
    }

    "provide level name and value maps" in {
      Logger.levels mustEqual Map(
        Level.ALL.value -> Level.ALL,
        Level.TRACE.value -> Level.TRACE,
        Level.DEBUG.value -> Level.DEBUG,
        Level.INFO.value -> Level.INFO,
        Level.WARNING.value -> Level.WARNING,
        Level.ERROR.value -> Level.ERROR,
        Level.CRITICAL.value -> Level.CRITICAL,
        Level.FATAL.value -> Level.FATAL,
        Level.OFF.value -> Level.OFF)
      Logger.levelNames mustEqual Map(
        "ALL" -> Level.ALL,
        "TRACE" -> Level.TRACE,
        "DEBUG" -> Level.DEBUG,
        "INFO" -> Level.INFO,
        "WARNING" -> Level.WARNING,
        "ERROR" -> Level.ERROR,
        "CRITICAL" -> Level.CRITICAL,
        "FATAL" -> Level.FATAL,
        "OFF" -> Level.OFF)
    }

    "figure out package names" in {
      val log1 = Logger(getClass)
      log1.name mustEqual "com.twitter.logging.LoggerSpec"
    }

    "log a message, with timestamp" in {
      Logger.clearHandlers
      myHandler = timeFrozenHandler
      log.addHandler(timeFrozenHandler)
      log.error("error!")
      parse() mustEqual List("ERR [20080329-05:53:16.722] (root): error!")
    }

    "configure logging" in {
      "file handler" in {
        withTempFolder {
          val config = new LoggerConfig {
            node = "com.twitter"
            level = Level.DEBUG
            handlers = new FileHandlerConfig {
              filename = folderName + "/test.log"
              roll = Policy.Never
              append = false
              formatter = new FormatterConfig {
                useFullPackageNames = true
                truncateAt = 1024
                prefix = "%s <HH:mm> %s"
              }
            } :: Nil
          }

          val log = Logger.configure(config)

          log.getLevel mustEqual Level.DEBUG
          log.getHandlers().length mustEqual 1
          val handler = log.getHandlers()(0).asInstanceOf[FileHandler]
          handler.filename mustEqual folderName + "/test.log"
          handler.append mustEqual false
          val formatter = handler.formatter
          formatter.formatPrefix(javalog.Level.WARNING, "10:55", "hello") mustEqual "WARNING 10:55 hello"
          log.name mustEqual "com.twitter"
          formatter.truncateAt mustEqual 1024
          formatter.useFullPackageNames mustEqual true
        }
      }

      "syslog handler" in {
        withTempFolder {
          val config = new LoggerConfig {
            node = "com.twitter"
            handlers = new SyslogHandlerConfig {
              formatter = new SyslogFormatterConfig {
                serverName = "elmo"
                priority = 128
              }
              server = "example.com"
              port = 212
            } :: Nil
          }

          val log = Logger.configure(config)
          log.getHandlers.length mustEqual 1
          val h = log.getHandlers()(0).asInstanceOf[SyslogHandler]
          h.dest.asInstanceOf[InetSocketAddress].getHostName mustEqual "example.com"
          h.dest.asInstanceOf[InetSocketAddress].getPort mustEqual 212
          val formatter = h.formatter.asInstanceOf[SyslogFormatter]
          formatter.serverName mustEqual Some("elmo")
          formatter.priority mustEqual 128
        }
      }

      "complex config" in {
        withTempFolder {
          val config = new LoggerConfig {
            level = Level.INFO
            handlers = new ThrottledHandlerConfig {
              durationMilliseconds = 60000
              maxToDisplay = 10
              handler = new FileHandlerConfig {
                filename = folderName + "/production.log"
                roll = Policy.SigHup
                formatter = new FormatterConfig {
                  truncateStackTracesAt = 100
                }
              }
            }
          } :: new LoggerConfig {
            node = "w3c"
            level = Level.OFF
            useParents = false
          } :: new LoggerConfig {
            node = "stats"
            level = Level.INFO
            useParents = false
            handlers = new ScribeHandlerConfig {
              formatter = BareFormatterConfig
              maxMessagesToBuffer = 100
              category = "cuckoo_json"
            }
          } :: new LoggerConfig {
            node = "bad_jobs"
            level = Level.INFO
            useParents = false
            handlers = new FileHandlerConfig {
              filename = folderName + "/bad_jobs.log"
              roll = Policy.Never
            }
          } :: Nil

          Logger.configure(config)
          Logger.get("").getLevel mustEqual Level.INFO
          Logger.get("w3c").getLevel mustEqual Level.OFF
          Logger.get("stats").getLevel mustEqual Level.INFO
          Logger.get("bad_jobs").getLevel mustEqual Level.INFO
          Logger.get("").getHandlers()(0) must haveClass[ThrottledHandler]
          Logger.get("").getHandlers()(0).asInstanceOf[ThrottledHandler].handler must haveClass[FileHandler]
          Logger.get("w3c").getHandlers().size mustEqual 0
          Logger.get("stats").getHandlers()(0) must haveClass[ScribeHandler]
          Logger.get("bad_jobs").getHandlers()(0) must haveClass[FileHandler]
        }
      }
      /*

      withTempFolder {
        // FIXME failing test to configure throttling
        val TEST_DATA =
          "node=\"net.lag\"\n" +
          "console on\n" +
          "throttle_period_msec=100\n" +
          "throttle_rate=10\n"

        val c = new Config
        c.load(TEST_DATA)
        val log = Logger.configure(c, false, true)

        log.getHandlers.length mustEqual 1
        val h = log.getHandlers()(0).asInstanceOf[ThrottledHandler]
        h.durationMilliseconds mustEqual 100
        h.maxToDisplay mustEqual 10
      }
      */
    }


/*
    "set two handlers on the same logger without resetting the level" in {
      val config = new LoggerConfig {
        override val level = Level.DEBUG
        override val handlers = new FileHandlerConfig {
          val filename = "foobar.log"
          val policy = Policy.Never
          val append = true
        }
      } :: new LoggerConfig {
        override val level = Level.FATAL
        override val handlers = new ScribeHandlerConfig {
          override val hostname = "fake"
          override val port = 8080
        }
      }

      val TEST_DATA =
        "filename=\"foobar.log\"\n" +
        "level=\"debug\"\n" +
        "scribe {\n" +
        "  scribe_server = \"fake:8080\"\n" +
        "  level=\"fatal\"\n" +
        "}\n"
      val log1 = Logger.configure(c, false, true)
      val log2 = Logger.configure(c.configMap("scribe"), false, false)
      log1.getLevel mustEqual Logger.DEBUG
      log2.getLevel mustEqual Logger.DEBUG
      log1.getHandlers()(0) must haveClass[FileHandler]
      log1.getHandlers()(0).getLevel mustEqual Logger.DEBUG
      log1.getHandlers()(1) must haveClass[ScribeHandler]
      log1.getHandlers()(1).getLevel mustEqual Logger.FATAL
    }
*/

  }
}



/*

import _root_.java.util.{Calendar, Date, TimeZone, logging => javalog}
import _root_.net.lag.configgy.Config
import _root_.net.lag.extensions._






class TimeWarpingFileHandler(filename: String, policy: Policy, append: Boolean, handleSighup: Boolean)
  extends FileHandler(filename, policy, new FileFormatter, append, handleSighup) {
  formatter.timeZone = "GMT"

  override def publish(record: javalog.LogRecord) = {
    record.setMillis(1206769996722L)
    super.publish(record)
  }
}

class ImmediatelyRollingFileHandler(filename: String, policy: Policy, append: Boolean)
      extends TimeWarpingFileHandler(filename, policy, append, false) {
  formatter.timeZone = "GMT"

  override def computeNextRollTime(): Long = System.currentTimeMillis + 100
}




  "Logging" should {


CONFIG



    "handle config errors" in {
      // should throw an exception because of the unknown attribute
      val TEST_DATA =
        "filename=\"foobar.log\"\n" +
        "level=\"debug\"\n" +
        "style=\"html\"\n"

      val c = new Config
      c.load(TEST_DATA)
      Logger.configure(c, false, false) must throwA(new LoggingException("Unknown logging config attribute(s): style"))
    }


}

*/
