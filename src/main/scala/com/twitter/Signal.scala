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

import java.lang.reflect
import scala.collection.mutable

object SignalHandlerFactory {
  def apply() = {
    // only one actual implementation for now
    SunSignalHandler.instantiate
  }
}

trait SignalHandler {
  def handle(signal: String, handlers: collection.Map[String, collection.Set[String => Unit]])
}

object SunSignalHandler {
  def instantiate = {
    try {
      Class.forName("sun.misc.Signal")
      Some(new SunSignalHandler)
    } catch {
      case ex: ClassNotFoundException => None
    }
  }
}

class SunSignalHandler extends SignalHandler {
  private val signalHandlerClass = Class.forName("sun.misc.SignalHandler")
  private val signalClass = Class.forName("sun.misc.Signal")
  private val handleMethod = signalClass.getMethod("handle", signalClass, signalHandlerClass)
  private val nameMethod = signalClass.getMethod("getName")

  def handle(signal: String, handlers: scala.collection.Map[String, scala.collection.Set[String => Unit]]) {
    val sunSignal = signalClass.getConstructor(classOf[String]).newInstance(signal).asInstanceOf[Object]
    val proxy = reflect.Proxy.newProxyInstance(signalHandlerClass.getClassLoader, Array[Class[_]](signalHandlerClass),
      new reflect.InvocationHandler {
        def invoke(proxy: Object, method: reflect.Method, args: Array[Object]) = {
          if (method.getName() == "handle") {
            handlers(signal).foreach { x =>
              x(nameMethod.invoke(args(0)).asInstanceOf[String])
            }
          }
          null
        }
      }).asInstanceOf[Object]

    handleMethod.invoke(null, sunSignal, proxy)
  }
}

object HandleSignal {
  private val handlers = mutable.HashMap[String, mutable.Set[String => Unit]]()

  def apply(posixSignal: String)(f: String => Unit) {
    if (!handlers.contains(posixSignal)) {
      handlers.synchronized {
        SignalHandlerFactory().foreach { _.handle(posixSignal, handlers) }
        handlers += ((posixSignal, mutable.HashSet[String => Unit]()))
      }
    }

    handlers.synchronized {
      handlers(posixSignal) += f
    }
  }

  def clear(posixSignal: String) {
    handlers.synchronized {
      handlers(posixSignal).clear
    }
  }
}
