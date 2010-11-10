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

import java.util.{logging => javalog}
import scala.collection.mutable

class ThrottledHandler(wrapped: Handler, val durationMilliseconds: Int, val maxToDisplay: Int) extends Handler(wrapped.formatter) {
  private class Throttle(now: Long) {
    var startTime: Long = now
    var count: Int = 0

    override def toString = "Throttle: startTime=" + startTime + " count=" + count
  }

  private val throttleMap = new mutable.HashMap[String, Throttle]

  def reset() {
    throttleMap.synchronized {
      for ((k, throttle) <- throttleMap) {
        throttle.startTime = 0
      }
    }
  }

  def close() = wrapped.close()
  def flush() = wrapped.flush()

  /**
   * Log a message, with sprintf formatting, at the desired level, and
   * attach an exception and stack trace.
   */
  def publish(record: javalog.LogRecord) = {
    val now = System.currentTimeMillis
    val throttle = throttleMap.synchronized { throttleMap.getOrElseUpdate(record.getMessage(), new Throttle(now)) }
    throttle.synchronized {
      if (now - throttle.startTime >= durationMilliseconds) {
        if (throttle.count > maxToDisplay) {
          val throttledRecord = new javalog.LogRecord(record.getLevel(), "(swallowed %d repeating messages)".format(throttle.count - maxToDisplay))
          throttledRecord.setLoggerName(record.getLoggerName())
          wrapped.publish(throttledRecord)
        }
        throttle.startTime = now
        throttle.count = 0
      }
      throttle.count += 1
      if (throttle.count <= maxToDisplay) {
        wrapped.publish(record)
      }
    }
  }
}
