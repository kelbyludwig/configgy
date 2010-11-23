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

import java.text.SimpleDateFormat
import java.util.{Date, GregorianCalendar, TimeZone, logging => javalog}
import java.util.regex.Pattern
import scala.collection.mutable
import com.twitter.json.Json
import extensions._

private[logging] object Formatter {
  // FIXME: might be nice to unmangle some scala names here.
  private[logging] def formatStackTrace(t: Throwable, limit: Int): mutable.ListBuffer[String] = {
    var out = new mutable.ListBuffer[String]
    if (limit > 0) {
      out ++= t.getStackTrace.map { elem => "    at %s".format(elem.toString) }
      if (out.length > limit) {
        out.trimEnd(out.length - limit)
        out += "    (...more...)"
      }
    }
    if ((t.getCause ne null) && (t.getCause ne t)) {
      out += "Caused by %s".format(t.getCause.toString)
      out ++= formatStackTrace(t.getCause, limit)
    }
    out
  }

  val dateFormatRegex = Pattern.compile("<([^>]+)>")
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
}

/**
 * A standard log formatter for scala. This extends the java built-in log formatter.
 *
 * Truncation, exception formatting, multi-line logging, and time zones
 * are handled in this class. Subclasses are called for formatting the
 * line prefix, formatting the date, and determining the line terminator.
 */
class Formatter(config: FormatterConfig) extends javalog.Formatter {

  def this() = this(new FormatterConfig)

  private val matcher = Formatter.dateFormatRegex.matcher(config.prefix)

  private val FORMAT = matcher.replaceFirst("%3\\$s")
  private val DATE_FORMAT = new SimpleDateFormat(if (matcher.find()) matcher.group(1) else "yyyyMMdd-HH:mm:ss.SSS")

  /**
   * Return the date formatter to use for log messages.
   */
  def dateFormat: SimpleDateFormat = DATE_FORMAT

  /**
   * Calendar to use for time zone display in date-time formatting.
   */
  val calendar = if (config.timezone.isDefined) {
    new GregorianCalendar(TimeZone.getTimeZone(config.timezone.get))
  } else {
    new GregorianCalendar
  }
  dateFormat.setCalendar(calendar)

  /**
   * Return the string to prefix each log message with, given a log level,
   * formatted date string, and package name.
   */
  def formatPrefix(level: javalog.Level, date: String, name: String): String = {
    val levelName = level match {
      // if it maps to one of our levels, use our name.
      case x: Level =>
        x.name
      case x: javalog.Level =>
        Logger.levelsMap.get(x.intValue) match {
          case None => "%03d".format(x.intValue)
          case Some(level) => level.name
        }
    }

    FORMAT.format(levelName, name, date)
  }

  /**
   * Return the line terminator (if any) to use at the end of each log
   * message.
   */
  def lineTerminator: String = "\n"

  /**
   * Return formatted text from a java LogRecord.
   */
  def formatText(record: javalog.LogRecord): String = {
    record match {
      case r: LazyLogRecord =>
        r.generate.toString
      case r: javalog.LogRecord =>
        r.getParameters match {
          case null =>
            r.getMessage
          case formatArgs =>
            String.format(r.getMessage, formatArgs: _*)
        }
    }
  }

  override def format(record: javalog.LogRecord): String = {
    val name = record.getLoggerName match {
      case null => "(root)"
      case "" => "(root)"
      case n => {
        val nameSegments = n.split("\\.")
        if (nameSegments.length >= 2) {
          if (config.useFullPackageNames) {
            nameSegments.slice(0, nameSegments.length - 1).mkString(".")
          } else {
            nameSegments(nameSegments.length - 2)
          }
        } else {
          n
        }
      }
    }

    var message = formatText(record)

    if ((config.truncateAt > 0) && (message.length > config.truncateAt)) {
      message = message.substring(0, config.truncateAt) + "..."
    }

    // allow multi-line log entries to be atomic:
    var lines = new mutable.ArrayBuffer[String]
    lines ++= message.split("\n")

    if (record.getThrown ne null) {
      lines += record.getThrown.toString
      lines ++= Formatter.formatStackTrace(record.getThrown, config.truncateStackTracesAt)
    }
    val prefix = formatPrefix(record.getLevel, dateFormat.format(new Date(record.getMillis)), name)
    lines.mkString(prefix, lineTerminator + prefix, lineTerminator)
  }
}

/**
 * Formatter that uses all the defaults.
 */
object BasicFormatter extends Formatter

/**
 * Formatter that only logs records with attached exceptions, and logs them in json.
 */
class ExceptionJsonFormatter extends Formatter {
  private def throwableToMap(wrapped: Throwable): collection.Map[String, Any] = {
    val rv = mutable.Map[String, Any]("class" -> wrapped.getClass().getName())
    if (wrapped.getMessage() != null) {
      rv += ("message" -> wrapped.getMessage())
    }
    rv += (("trace", wrapped.getStackTrace().map(_.toString())))
    if (wrapped.getCause() != null) {
      rv += (("cause", throwableToMap(wrapped.getCause())))
    }
    rv
  }

  override def format(record: javalog.LogRecord) = {
    val thrown = record.getThrown()
    if (thrown != null) {
      val map = mutable.Map[String, Any]()
      map ++= throwableToMap(thrown)
      map += ("level" -> record.getLevel())
      map += (("created_at", record.getMillis() / 1000))
      Json.build(map).toString + lineTerminator
    } else {
      ""
    }
  }
}

/**
 * Formatter that logs only the text of a log message, with no prefix (no date, etc).
 */
object BareFormatter extends Formatter {
  override def format(record: javalog.LogRecord) = formatText(record) + lineTerminator
}
