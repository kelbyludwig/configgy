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
package config

import java.net.InetAddress

class LoggerConfig {
  /**
   * Name of the logging node. The default ("") is the top-level logger.
   */
  val node: String = ""

  /**
   * Log level for this node. Leaving it null is java's secret signal to use the parent logger's
   * level.
   */
  val level: Level = null

  /**
   * Where to send log messages.
   */
  implicit val handlers: List[Handler] = Nil

  /**
   * Override to have log messages stop at this node. Otherwise they are passed up to parent
   * nodes.
   */
  val useParents = true
}

class FormatterConfig {
  /**
   * Should dates in log messages be reported in a different time zone rather than local time?
   * If set, the time zone name must be one known by the java `TimeZone` class.
   */
  val timezone: Option[String] = None

  /**
   * Truncate log messages after N characters. 0 = don't truncate (the default).
   */
  val truncateAt = 0

  /**
   * Truncate stack traces in exception logging (line count).
   */
  val truncateStackTracesAt = 30

  /**
   * Use full package names like "com.example.thingy" instead of just the toplevel name like
   * "thingy"?
   */
  val useFullPackageNames = false

  /**
   * Format for the log-line prefix, if any.
   *
   * There are two positional format strings (printf-style): the name of the level being logged
   * (for example, "ERROR") and the name of the package that's logging (for example, "jobs").
   *
   * A string in `<` angle brackets `>` will be used to format the log entry's timestamp, using
   * java's `SimpleDateFormat`.
   *
   * For example, a format string of:
   *
   *     "%.3s [<yyyyMMdd-HH:mm:ss.SSS>] %s: "
   *
   * will generate a log line prefix of:
   *
   *     "ERR [20080315-18:39:05.033] jobs: "
   */
  val prefix = "%.3s [<yyyyMMdd-HH:mm:ss.SSS>] %s: "

  def formatter = new Formatter(this)
}

abstract class SyslogHandlerConfig extends FormatterConfig {
  val useIsoDateFormat = true
  val priority = SyslogHandler.PRIORITY_USER
  val hostname = InetAddress.getLocalHost().getHostName()
  val serverName: Option[String] = None
  val server: String

  def handler = new SyslogHandler(this)
}

abstract class FileHandlerConfig {
  val filename: String
  val roll: Policy
  val formatter: Formatter = BasicFormatter
  val append: Boolean
  val rotateCount: Int = -1

  def handler = new FileHandler(this)
}
