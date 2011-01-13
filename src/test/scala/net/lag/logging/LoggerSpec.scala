/*
 * Copyright 2011 Kevin Oliver <koliver@twitter.com>
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
package net.lag.logging

import java.util.ArrayList
import java.util.concurrent._
import org.specs._

object LoggerSpec extends Specification {

  "Logger" should {

    "get single-threaded return the same value" in {
      val loggerFirst = Logger.get("getTest")
      loggerFirst must notBeNull

      val loggerSecond = Logger.get("getTest")
      loggerSecond must be(loggerFirst)
    }

    "get multi-threaded return the same value" in {
      val numThreads = 10
      val latch = new CountDownLatch(1)

      // queue up the workers
      val executorService = Executors.newFixedThreadPool(numThreads)
      val futureResults = new ArrayList[Future[Logger]](numThreads)
      for (i <- 0.until(numThreads)) {
        val future = executorService.submit(new Callable[Logger]() {
          def call(): Logger = {
            latch.await(10, TimeUnit.SECONDS)
            return Logger.get("concurrencyTest")
          }
        })
        futureResults.add(future)
      }
      executorService.shutdown
      // let them rip, and then wait for em to finish
      latch.countDown
      executorService.awaitTermination(10, TimeUnit.SECONDS) must beTrue

      // now make sure they are all the same reference
      val expected = futureResults.get(0).get
      for (i <- 1.until(numThreads)) {
        val result = futureResults.get(i).get
        result must be(expected)
      }
    }

  }

}
