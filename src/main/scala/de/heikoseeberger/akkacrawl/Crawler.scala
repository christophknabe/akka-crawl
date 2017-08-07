package de.heikoseeberger.akkacrawl

import akka.actor.{ Actor, ActorLogging, PoisonPill, Props, ReceiveTimeout }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.{ ActorMaterializer, ActorMaterializerSettings }
import akka.stream.scaladsl.{ Sink }

import scala.util.{ Failure, Success }

import scala.concurrent.duration.{ FiniteDuration }

object Crawler {

  def props(url: Uri, responseTimeout: FiniteDuration, depth: Int = 0) = Props(new Crawler(url, responseTimeout, depth))

  /**A RegEx pattern for recognizing HTTP links in a HTML page.*/
  val linkPattern = """"(https?://[^" ]+)"""".r

  /** Returns true, if the URI shows to a web page, which will probably contain more URIs. */
  def isWorthToFollow(uri: Uri): Boolean = {
    if (!Set("http", "https").contains(uri.scheme)) return false
    val path = uri.path.toString()
    if (path == "" || path == "/") return true
    val splittedPath = path.split('/')
    val lastPathElem = splittedPath.apply(splittedPath.length - 1)
    if (lastPathElem.isEmpty) return true
    val extensionBeginIndex: Int = lastPathElem.lastIndexOf('.')
    if (extensionBeginIndex < 0) return true
    val extension = lastPathElem.substring(extensionBeginIndex)
    Set(".html", ".shtml", ".jsp", ".asp") contains extension
  }

}

/**Scans the page from the URI, which has the given link depth. If in the page it encounters URIs, they will be sent to the CrawlerManager in order to scan them, too.
  *
  * @param responseTimeout duration to wait before the page at the URI is considered as not retrievable.*/
class Crawler(uri: Uri, responseTimeout: FiniteDuration, depth: Int)
    extends Actor
    with ActorLogging {

  import Crawler._
  import akka.pattern.pipe
  import context.dispatcher
  import scala.concurrent.duration._

  private final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

  private val http = Http(context.system)
  log.debug("Crawler for {} created", uri)

  private val startTime = System.currentTimeMillis

  override def preStart() = {
    http.singleRequest(HttpRequest(uri = uri)).pipeTo(self)
    context.setReceiveTimeout(responseTimeout)
  }

  override def receive: Receive = {
      case HttpResponse(StatusCodes.OK, _, entity, _) =>
        log.debug("Getting page {} ...", uri)
        context.setReceiveTimeout(Duration.Undefined)
        entity.dataBytes.runForeach { chunk =>
          for (matched <- linkPattern.findAllMatchIn(chunk.utf8String)) {
            val stringUri = matched.group(1)
            val linkUri = Uri(stringUri)
            if (isWorthToFollow(linkUri)) {
              context.parent ! CrawlerManager.ScanPage(linkUri, depth + 1)
            }
          }
        }.onComplete {
          case Success(done) =>
            //Source with data bytes read completely.
            context.parent !
              CrawlerManager.PageScanned(elapsedMillis, uri, depth)
            self ! PoisonPill
          case Failure(t) =>
            log.debug("GET request {} dataBytes read completed with Failure {}", uri, t)
            self ! PoisonPill
        }

      case HttpResponse(statusCode, headers, entity, _) if statusCode.isRedirection =>
        val locationString = headers.filter(_.is("location")).map(_.value).mkString
        log.debug("""GET request {} was redirected with status "{}" to {}""", uri, statusCode, locationString)

        val linkUri = Uri(locationString)
        if (isWorthToFollow(linkUri)) {
          context.parent ! CrawlerManager.ScanPage(linkUri, depth + 1)
        }
        entity.dataBytes.runWith(Sink.ignore)
        self ! PoisonPill

      case HttpResponse(statusCode, _, entity, _) =>
        log.debug("GET request {} was answered with error {}", uri, statusCode)
        entity.dataBytes.runWith(Sink.ignore)
        self ! PoisonPill

      case ReceiveTimeout =>
        log.debug("GET request {} was not answered within {} millis.", uri, elapsedMillis)
        self ! PoisonPill
    }

  private def elapsedMillis = System.currentTimeMillis() - startTime

}
