package de.heikoseeberger.akkacrawl

import java.net.URL

import akka.actor.Actor.Receive
import akka.actor.{ Actor, ActorLogging, Props, Status }

import scala.concurrent.duration.FiniteDuration

object CrawlerManager {

  def props(connectTimeout: FiniteDuration, getTimeout: FiniteDuration): Props =
    Props(new CrawlerManager(connectTimeout, getTimeout))

  case class CheckUrl(url: URL, depth: Int)
}

class CrawlerManager(connectTimeout: FiniteDuration, getTimeout: FiniteDuration)
    extends Actor
    with ActorLogging {

  import CrawlerManager._

  val visited = new scala.collection.mutable.HashMap[URL, Int]()

  log.debug("Crawler Manager created")

  override def receive: Receive = {
    case CheckUrl(url: URL, depth: Int) =>
      log.debug("Crawler Manager queried for [{}], [{}]", url, depth)
      if (!visited.contains(url)) {
        visited += (url -> depth)
        context.actorOf(Crawler.props(url, connectTimeout, getTimeout, depth + 1))
      }
  }
}