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

import akka.actor.{ Actor, ActorLogging, Props, Status }
import akka.http.Http
import akka.http.model.headers.Host
import akka.http.model.{ HttpRequest, HttpResponse, StatusCodes }
import akka.io.IO
import akka.pattern.{ ask, pipe }
import akka.stream.FlowMaterializer
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.Timeout
import java.net.URL
import scala.concurrent.Future
import scala.concurrent.duration.{ Duration, FiniteDuration }

object Crawler {

  def props(url: URL, connectTimeout: FiniteDuration, getTimeout: FiniteDuration, depth: Int = 0): Props =
    Props(new Crawler(url, connectTimeout, getTimeout, depth))

  def get(
    url:       URL,
    responses: Source[(HttpResponse, Any)],
    requests:  Sink[(HttpRequest, Any)]
  )(implicit fm: FlowMaterializer): Future[HttpResponse] = {
    val path = if (url.getPath == "") "/" else url.getPath
    val request = HttpRequest(uri = path, headers = List(Host(url.getHost)))
    Source(List(request))
      .map(_ -> None)
      .runWith(requests)
    responses
      .map(_._1)
      .runWith(Sink.head)
  }

  /**A RegEx pattern for extracting HTTP links from a HTML page.*/
  val linkPattern = """"(http://[^" ]+)"""".r

  def isWorthToFollow(url: URL): Boolean = {
    val path = url.getPath
    if(path=="" || path=="/") return true
    val splittedPath = path.split('/')
    val lastPathElem = splittedPath.apply(splittedPath.length-1)
    if(lastPathElem.isEmpty) return true
    val extensionBeginIndex: Int = lastPathElem.lastIndexOf('.')
    if(extensionBeginIndex < 0) return true
    val extension: String = lastPathElem.substring(extensionBeginIndex)
    Set(".html", ".shtml", ".jsp", ".asp") contains extension
  }

}

class Crawler(url: URL, connectTimeout: FiniteDuration, getTimeout: FiniteDuration, depth: Int)
    extends Actor
    with ActorLogging {

  import Crawler._
  import context.dispatcher

  private val startTime = System.currentTimeMillis

  private implicit val flowMaterializer = FlowMaterializer()

  IO(Http)(context.system)
    .ask(Http.Connect(url.getHost))(connectTimeout)
    .mapTo[Http.OutgoingConnection]
    .pipeTo(self)

  log.debug("Crawler for [{}] created", url)

  override def receive: Receive =
    connecting

  private def connecting: Receive = {
    case Http.OutgoingConnection(_, _, responses, requests) =>
      log.debug("Successfully connected to [{}]", url.getHost)
      get(url, Source(responses), Sink(requests)).pipeTo(self)
      context.setReceiveTimeout(getTimeout)
      context.become(getting)

    case Status.Failure(cause) =>
      log.error(cause, "Couldn't connect to [{}]!", url.getHost)
      context.stop(self)
  }

  private def getting: Receive = {
    case HttpResponse(StatusCodes.OK, _, entity, _) =>
      log.debug("Successfully got [{}]", url)
      context.setReceiveTimeout(Duration.Undefined)
      for (chunk <- entity.dataBytes) {
        for (matched <- linkPattern.findAllMatchIn(chunk.utf8String)) {
          val stringUrl = matched.group(1)
          val linkUrl: URL = new URL(stringUrl)
          if(isWorthToFollow(linkUrl)){
            context.parent ! CrawlerManager.CheckUrl(linkUrl, depth + 1)
          }
        }
      }
      context.actorSelection("/user/stats") ! CrawlerManager.Finished(System.currentTimeMillis() - startTime, url, depth)

    case HttpResponse(other, _, _, _) =>
      log.error("Server [{}] responded with [{}] to GET [{}]", url.getHost, other, url.getPath)
      context.stop(self)

    case Timeout =>
      log.error("Server [{}] didn't respond to GET [{}] within [{}]", url.getHost, url.getPath, connectTimeout)
      context.stop(self)
  }
}
