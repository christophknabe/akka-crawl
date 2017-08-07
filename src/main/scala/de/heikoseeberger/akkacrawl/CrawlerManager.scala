package de.heikoseeberger.akkacrawl

import akka.http.scaladsl.model.Uri
import akka.actor.{ Actor, ActorLogging, PoisonPill, Props }

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.FiniteDuration

object CrawlerManager {

  def props(responseTimeout: FiniteDuration): Props = Props(new CrawlerManager(responseTimeout))

  /**
    * A command to scan the page with the given URI. The depth is the link distance from the start URI of the main App.
    * This command has a lower priority than the others, as it creates new work, whereas the others register and print work already done.
    */
  case class ScanPage(uri: Uri, depth: Int)

  /**A command to archive, that the web page at the given URI and link depth was successfully scanned.*/
  case class PageScanned(durationMillis: Long, uri: Uri, depth: Int) extends akka.dispatch.ControlMessage {
    def format: String = {
      f"${depth}%2d ${durationMillis}%5dms ${uri}"
    }
  }

  /**A command to print a final statistics and to terminate the main app.*/
  case object PrintFinalStatistics extends akka.dispatch.ControlMessage

}

/**An actor, which manages many actors-per-request to crawl the whole web.*/
class CrawlerManager(responseTimeout: FiniteDuration) extends Actor with ActorLogging {

  private val startMillis: Long = System.currentTimeMillis

  import CrawlerManager._

  /**A memory with all tried URIs in order to avoid link recursion.*/
  private val triedUris = new scala.collection.mutable.HashSet[Uri]()

  /**A memory about all web pages, which have been found and successfully scanned for further URIs.*/
  private val archive = new ArrayBuffer[PageScanned]()

  log.debug(s"Crawler Manager created with responseTimeout $responseTimeout.")

  override def receive: Receive = {
    case ScanPage(uri: Uri, depth: Int) =>
      log.debug("Crawler Manager queried for {} at depth {}.", uri, depth)
      if (!triedUris.contains(uri)) {
        triedUris += uri
        context.actorOf(Crawler.props(uri, responseTimeout, depth))
      }

    case f: PageScanned =>
      archive += f
      println(f.format)

    case PrintFinalStatistics =>
      val endMillis = System.currentTimeMillis
      val durationMillis = endMillis - startMillis
      val summedUpMillis = archive.map(_.durationMillis).foldLeft(0: Long)((a, b) => a + b)
      println("=" * 80 + s"\nSummary: Scanned ${archive.length} pages in ${durationMillis} millis (summedUp: ${summedUpMillis} millis).\n" + "=" * 80 + "\n")
      _terminate()
  }

  private def _terminate(): Unit = {
    import scala.concurrent.duration._
    import akka.http.scaladsl.Http
    val system = context.system
    val http = Http(system)
    //Give the actor some time to terminate before terminating the actor system:
    import system.dispatcher //as implicit ExecutionContext
    system.scheduler.scheduleOnce(2.seconds) {
      log.info("Going to shut down the system...")
      http.shutdownAllConnectionPools()
      system.terminate()
    }
    self ! PoisonPill
  }

}
