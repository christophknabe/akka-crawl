package de.heikoseeberger.akkacrawl

import java.net.URL

import akka.actor.Actor.Receive
import akka.actor.{ Actor, ActorLogging, Props, Status }

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.FiniteDuration

object CrawlerManager {

  def props(connectTimeout: FiniteDuration, getTimeout: FiniteDuration): Props =
    Props(new CrawlerManager(connectTimeout, getTimeout))

  case class CheckUrl(url: URL, depth: Int)

  case class Finished(durationMillis: Long, url: URL, depth: Int)

  case object PrintStatistics

  case object PrintFinalStatistics
}

class CrawlerManager(connectTimeout: FiniteDuration, getTimeout: FiniteDuration)
    extends Actor
    with ActorLogging {

  private val startMillis: Long = System.currentTimeMillis

  import CrawlerManager._

  private val visited = new scala.collection.mutable.HashMap[URL, Int]()

  private val archive = new ArrayBuffer[Finished]()

  log.debug("Crawler Manager created")

  override def receive: Receive = {
    case CheckUrl(url: URL, depth: Int) =>
      log.debug("Crawler Manager queried for [{}], [{}]", url, depth)
      if (!visited.contains(url)) {
        visited += (url -> depth)
        context.actorOf(Crawler.props(url, connectTimeout, getTimeout, depth))
      }
    case f @ Finished(durationMillis: Long, url: URL, _) =>
      log.debug("Finished [{}] in [{}]", url, durationMillis)
      archive += f

    case PrintStatistics =>
      val endMillis = System.currentTimeMillis
      val durationMillis = endMillis - startMillis
      val summedUpMillis = archive.map(_.durationMillis).foldLeft(0: Long)((a, b) => a + b)
      //val messages = sorted.map(_.asMessage).mkString("\n")
      println(
        s"Summary: Crawled ${archive.length} URIs in ${durationMillis} millis (summedUp: ${summedUpMillis} millis)."
      )
    case PrintFinalStatistics =>
      val endMillis = System.currentTimeMillis
      val durationMillis = endMillis - startMillis
      val summedUpMillis = archive.map(_.durationMillis).foldLeft(0: Long)((a, b) => a + b)
      println(
        s"Summary: Crawled ${archive.length} URIs in ${durationMillis} millis (summedUp: ${summedUpMillis} millis)."
      )
      println(archive.sortBy(_.depth).map(_.toString).mkString("\n"))
      context.system.shutdown()
  }
}