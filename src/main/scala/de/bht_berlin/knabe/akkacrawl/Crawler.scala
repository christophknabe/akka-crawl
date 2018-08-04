package de.bht_berlin.knabe.akkacrawl

import akka.http.scaladsl.model.Uri
import akka.actor.{Actor, ActorLogging, PoisonPill, Props}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Materializer}

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
    def format: String = f"$depth%2d $durationMillis%5dms $uri"
  }

  /**A command to print a final summary of scanned pages and to proceed in the finishing mode.*/
  case object PrintScanSummary extends akka.dispatch.ControlMessage

  /**A command to check if the system is inactive now, and to terminate the main App, if yes.*/
  case object ShutdownIfResponsible

}

/**An actor, which manages many actors-per-request to crawl the whole web.*/
class Crawler(responseTimeout: FiniteDuration) extends Actor with ActorLogging {

  private val line = "=" * 80

  import Crawler._

  /**A memory with all tried URIs in order to avoid link recursion.*/
  private val triedUris = new scala.collection.mutable.HashSet[Uri]()

  /**A memory about all web pages, which have been found and successfully scanned for further URIs.*/
  private val scannedPages = new ArrayBuffer[PageScanned]()

  //TODO Create the ActorMaterializer only once and share it. 2018-07-28. See https://github.com/akka/akka/issues/18797#issuecomment-157888432
  private final val materializer: Materializer = ActorMaterializer(ActorMaterializerSettings(context.system))

  log.debug(s"Crawler created with responseTimeout $responseTimeout.")
  println("Successfully Scanned Pages:\n==========================\n\nLvl Duratn URI\n=== ====== ===")

  private val startMillis: Long = System.currentTimeMillis

  private var lastCommandReceivedMillis: Long = System.currentTimeMillis

  override def receive: Receive = {
    case ScanPage(uri: Uri, depth: Int) =>
      log.debug("Crawler queried for {} at depth {}.", uri, depth)
      if (!triedUris.contains(uri)) {
        triedUris += uri
        context.actorOf(Scanner.props(uri, responseTimeout, depth, materializer))
      }

    case f: PageScanned =>
      scannedPages += f
      println(f.format)

    case PrintScanSummary =>
      val endMillis = System.currentTimeMillis
      lastCommandReceivedMillis = endMillis
      val elapsedSeconds = roundToSeconds(endMillis - startMillis)
      val summedUpSeconds = roundToSeconds(scannedPages.map(_.durationMillis).sum)
      println(s"$line\nSummary: Scanned ${scannedPages.length} pages in $elapsedSeconds seconds (summedUp: $summedUpSeconds seconds).\n$line\n")
      println("=======================Unprocessed Inbox commands:====================")
      self ! ShutdownIfResponsible
      context become finishing

    case unexpected =>
      log.error("Unexpected {}", unexpected)
      self ! PrintScanSummary
  }

  /**Number of unprocessed messages encountered in the Inbox during the finishing phase.*/
  private var unprocessedCount: Int = 0

  def finishing: Receive = {
    case ShutdownIfResponsible =>
      println(line)
      val secondsSinceLastCommand = roundToSeconds(System.currentTimeMillis - lastCommandReceivedMillis)
      if(secondsSinceLastCommand > 5){
        _terminate()
      }else{
        import scala.concurrent.duration._
        context.system.scheduler.scheduleOnce(5.seconds, self, ShutdownIfResponsible)(context.dispatcher, self)
      }
    case x @ (_: ScanPage | _: PageScanned) =>
      lastCommandReceivedMillis = System.currentTimeMillis
      unprocessedCount += 1
      println(s"$unprocessedCount. unprocessed: $x")

    case unexpected =>
      log.error("Unexpected {}", unexpected)
      _terminate()
  }

  /**Rounds a millisecond value to the nearest second value.*/
  private def roundToSeconds(millis: Long): Long = (millis + 500) / 1000

  private def _terminate(): Unit = {
    import scala.concurrent.duration._
    import akka.http.scaladsl.Http
    val system = context.system
    import system.dispatcher //as implicit ExecutionContext
    val http = Http(system)
    log.info("Shut down all HTTP connection pools...")
    for {
      _ <- http.shutdownAllConnectionPools()
      _ = self ! PoisonPill
    } yield system.scheduler.scheduleOnce(20.seconds) { //Give the connections double time of their idle timeout to be closed before terminating the actor system:
      log.info("Shut down the ActorSystem...")
      system.terminate()
    }
  }

}
