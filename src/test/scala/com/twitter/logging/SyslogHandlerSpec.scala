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

import java.net.{DatagramPacket, DatagramSocket, InetSocketAddress}
import java.util.{logging => javalog}
import org.specs.Specification
import extensions._

class SyslogHandlerSpec extends Specification {
  def config(_server: String, _serverName: Option[String], _useIso: Boolean) = new SyslogHandlerConfig {
    override val timezone = Some("UTC")
    override val useIsoDateFormat = _useIso
    val server = _server
    override val serverName = _serverName
    override val hostname = "raccoon.local"
  }

  val record1 = new javalog.LogRecord(Level.FATAL, "fatal message!")
  record1.setLoggerName("net.lag.whiskey.Train")
  record1.setMillis(1206769996722L)
  val record2 = new javalog.LogRecord(Level.ERROR, "error message!")
  record2.setLoggerName("net.lag.whiskey.Train")
  record2.setMillis(1206769996722L)

  "SyslogHandler" should {
    "write syslog entries" in {
      // start up new syslog listener
      val serverSocket = new DatagramSocket
      val serverPort = serverSocket.getLocalPort

      var syslog = new SyslogHandler(config("localhost:" + serverPort, None, true))
      syslog.publish(record1)
      syslog.publish(record2)

      Future.sync
      val p = new DatagramPacket(new Array[Byte](1024), 1024)
      serverSocket.receive(p)
      new String(p.getData, 0, p.getLength) mustEqual "<9>2008-03-29T05:53:16 raccoon.local whiskey: fatal message!"
      serverSocket.receive(p)
      new String(p.getData, 0, p.getLength) mustEqual "<11>2008-03-29T05:53:16 raccoon.local whiskey: error message!"
    }

    "with server name" in {
      // start up new syslog listener
      val serverSocket = new DatagramSocket
      val serverPort = serverSocket.getLocalPort

      var syslog = new SyslogHandler(config("localhost:" + serverPort, Some("pingd"), true))
      syslog.publish(record1)

      Future.sync
      val p = new DatagramPacket(new Array[Byte](1024), 1024)
      serverSocket.receive(p)
      new String(p.getData, 0, p.getLength) mustEqual "<9>2008-03-29T05:53:16 raccoon.local [pingd] whiskey: fatal message!"
    }

    "with BSD time format" in {
      // start up new syslog listener
      val serverSocket = new DatagramSocket
      val serverPort = serverSocket.getLocalPort

      var syslog = new SyslogHandler(config("localhost:" + serverPort, None, false))
      syslog.publish(record1)

      Future.sync
      val p = new DatagramPacket(new Array[Byte](1024), 1024)
      serverSocket.receive(p)
      new String(p.getData, 0, p.getLength) mustEqual "<9>Mar 29 05:53:16 raccoon.local whiskey: fatal message!"
    }
  }
}
