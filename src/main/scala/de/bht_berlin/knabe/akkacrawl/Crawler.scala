package de.bht_berlin.knabe.akkacrawl

import akka.http.scaladsl.model.Uri
import akka.actor.{ Actor, ActorLogging, PoisonPill, Props }

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.FiniteDuration

object Crawler {

  def props(responseTimeout: FiniteDuration): Props = Props(new Crawler(responseTimeout))

  /**
   * A command to scan the page at the given URI. The depth is the link distance from the start URI of the main App.
   * This command has a lower priority than the others, as it creates new work, whereas the others register and print work already done.
   */
  case class ScanPage(uri: Uri, depth: Int)

  /**A command to archive, that the web page at the given URI and link depth was successfully scanned.*/
  case class PageScanned(durationMillis: Long, uri: Uri, depth: Int) extends akka.dispatch.ControlMessage {
    def format: String = {
      f"${depth}%2d ${durationMillis}%5dms ${uri}"
    }
  }

  /**A command to print a final summary of scanned pages and to proceed in the finishing mode.*/
  case object PrintScanSummary extends akka.dispatch.ControlMessage

  /**A command to print all unprocessed ScanPage commands from the Inbox, and a summary about them, and to terminate the main App.*/
  case object PrintUnprocessedSummary

}

/**An actor, which manages many actors-per-request to crawl the whole web.*/
class Crawler(responseTimeout: FiniteDuration) extends Actor with ActorLogging {

  val line = "=" * 80

  import Crawler._

  /**A memory with all tried URIs in order to avoid link recursion.*/
  private val triedUris = new scala.collection.mutable.HashSet[Uri]()

  /**A memory about all web pages, which have been found and successfully scanned for further URIs.*/
  private val scannedPages = new ArrayBuffer[PageScanned]()

  log.debug(s"Crawler Manager created with responseTimeout $responseTimeout.")
  println("Successfully Scanned Pages:\n==========================\n\nLvl Duratn URI\n=== ====== ===")

  private val startMillis: Long = System.currentTimeMillis

  override def receive: Receive = {
    case ScanPage(uri: Uri, depth: Int) =>
      log.debug("Crawler Manager queried for {} at depth {}.", uri, depth)
      if (!triedUris.contains(uri)) {
        triedUris += uri
        context.actorOf(Scanner.props(uri, responseTimeout, depth))
      }

    case f: PageScanned =>
      scannedPages += f
      println(f.format)

    case PrintScanSummary =>
      val endMillis = System.currentTimeMillis
      val elapsedSeconds = roundToSeconds(endMillis - startMillis)
      val summedUpSeconds = roundToSeconds(scannedPages.map(_.durationMillis).sum)
      println(s"$line\nSummary: Scanned ${scannedPages.length} pages in $elapsedSeconds seconds (summedUp: $summedUpSeconds seconds).\n$line\n")
      println("=======================Unprocessed Inbox commands:====================")
      self ! PrintUnprocessedSummary
      context become finishing
  }

  /**Number of unprocessed messages encountered in the Inbox during the finishing phase.*/
  private var unprocessedCount: Int = 0

  def finishing: Receive = {
    case PrintUnprocessedSummary =>
      println(line)
      println("All unprocessed inbox commands listed above.")
      val unscannedPages = triedUris.toSet - (scannedPages.map(_.uri).toSet)
      println(line)
      println(s"Unscanned (not found | not completed) pages:")
      println(line)
      _terminate()
    case x @ (_: ScanPage | _: PageScanned) =>
      unprocessedCount += 1
      println(s"$unprocessedCount. unprocessed: $x")
  }

  /**Rounds a millisecond value to the nearest second value.*/
  private def roundToSeconds(millis: Long): Long = (millis + 500) / 1000

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
