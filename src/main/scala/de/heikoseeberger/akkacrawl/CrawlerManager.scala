package de.heikoseeberger.akkacrawl

import java.net.URL

import akka.actor.Actor.Receive
import akka.actor.{ Actor, ActorLogging, Props, Status }

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.FiniteDuration

object CrawlerManager {

  def props(connectTimeout: FiniteDuration, getTimeout: FiniteDuration): Props =
    Props(new CrawlerManager(connectTimeout, getTimeout))

  case class ScanPage(url: URL, depth: Int)

  case class PageScanned(durationMillis: Long, url: URL, depth: Int) {
    def format: String = {
      f"${depth}%2d ${durationMillis}%5dms ${url}"
    }
  }

  case object PrintFinalStatistics
}

class CrawlerManager(connectTimeout: FiniteDuration, getTimeout: FiniteDuration)
    extends Actor
    with ActorLogging {

  private val startMillis: Long = System.currentTimeMillis

  import CrawlerManager._

  private val triedUrls = new scala.collection.mutable.HashMap[URL, Int]()

  private val archive = new ArrayBuffer[PageScanned]()

  log.debug(s"Crawler Manager created with connect timeout $connectTimeout and get timeout $getTimeout.")

  override def receive: Receive = {
    case ScanPage(url: URL, depth: Int) =>
      log.debug("Crawler Manager queried for {} at depth {}.", url, depth)
      if (!triedUrls.contains(url)) {
        triedUrls += (url -> depth)
        context.actorOf(Crawler.props(url, connectTimeout, getTimeout, depth))
      }

    case f @ PageScanned(durationMillis: Long, url: URL, _) =>
      log.debug("PageScanned page {} in {} millis.", url, durationMillis)
      archive += f
      println(f.format)

    case PrintFinalStatistics =>
      val endMillis = System.currentTimeMillis
      val durationMillis = endMillis - startMillis
      val summedUpMillis = archive.map(_.durationMillis).foldLeft(0: Long)((a, b) => a + b)
      println("="*80 + s"\nSummary: Found ${archive.length} pages in ${durationMillis} millis (summedUp: ${summedUpMillis} millis).\n" + "="*80 + "\n")
      context.system.shutdown()
  }

}
