/*
 * Copyright 2014 Heiko Seeberger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.heikoseeberger.akkacrawl

import akka.actor.ActorSystem
import java.net.URL
import scala.io.StdIn
import scala.util.Try

object AkkaCrawlApp {

  def main(args: Array[String]) {
    val urlArg = args.headOption.getOrElse(sys.error("Initial URL missing!"))
    val url = Try(new URL(urlArg)).getOrElse(sys.error(s"Malformed initial URL [$urlArg]"))

    val system = ActorSystem("akka-crawl")
    val settings = Settings(system)
    val crawlerManager = system.actorOf(CrawlerManager.props(settings.connectTimeout, settings.getTimeout))
    val statsCollector = system.actorOf(CrawlerManager.props(settings.connectTimeout, settings.getTimeout), "stats")

    crawlerManager ! CrawlerManager.CheckUrl(url, 0)

    while (StdIn.readLine("<Q>ENTER to exit ...\n") == "") {
      statsCollector ! CrawlerManager.PrintStatistics
    }
    statsCollector ! CrawlerManager.PrintFinalStatistics
    //    system.shutdown()
    system.awaitTermination()
  }
}
