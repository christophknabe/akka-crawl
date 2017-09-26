package de.bht_berlin.knabe.akkacrawl

import java.nio.charset.Charset

import akka.actor.{ Actor, ActorLogging, PoisonPill, Props, ReceiveTimeout }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model._
import akka.stream.{ ActorMaterializer, ActorMaterializerSettings }
import akka.stream.scaladsl.{ Sink }

import scala.util.{ Failure, Success }
import scala.concurrent.duration.FiniteDuration

object Crawler {

  def props(url: Uri, responseTimeout: FiniteDuration, depth: Int = 0) = Props(new Crawler(url, responseTimeout, depth))

  /** A RegEx pattern for recognizing links in a HTML page. */
  val linkPattern = """href="([^"]*)"""".r

  val UTF8 = Charset.forName("UTF8")

  /**
   * Parses the uriString to a Uri.
   *
   * @return The parsed URI, resolved against the given baseUri, if it shows to a web page, which will probably contain more URIs. None otherwise or if an exception occured during parsing.
   *         Any fragment indications by # and any query parameters introduced by ? will be stripped off the returned Uri.
   */
  def worthToFollowUri(uriString: String, baseUri: Uri): Option[Uri] = {
    val completeUri = try {
      Uri.parseAndResolve(uriString, baseUri, UTF8, Uri.ParsingMode.Relaxed)
    } catch {
      case ex: Exception => return None
    }
    val resultUri = completeUri.copy(fragment       = None, rawQueryString = None)
    try {
      if (!Set("http", "https").contains(resultUri.scheme)) return None
      val result = Some(resultUri)
      val path = resultUri.path
      if (path.isEmpty || path == Path./) {
        return result
      }
      val splittedPath = path.toString.split('/')
      if (splittedPath.length < 1) {
        return result
      }
      val lastPathElem = splittedPath.apply(splittedPath.length - 1)
      if (lastPathElem.isEmpty) {
        return result
      }
      val extensionBeginIndex: Int = lastPathElem.lastIndexOf('.')
      if (extensionBeginIndex < 0) {
        return result
      }
      val extension = lastPathElem.substring(extensionBeginIndex)
      if (Set(".html", ".shtml", ".jsp", ".asp", ".php") contains extension) result else None
    } catch {
      case ex: Exception =>
        throw new RuntimeException(s"Parsing URI $completeUri failed.", ex)
    }
  }

}

/**
 * Scans the page from the URI, which has the given link depth. If in the page it encounters href-s to URIs, they will be sent to the CrawlerManager in order to scan them, too.
 *
 * @param responseTimeout duration to wait before the page at the URI is considered as not retrievable.
 */
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
      entity.dataBytes.runForeach {
        chunk =>
          for (matched <- linkPattern.findAllMatchIn(chunk.utf8String)) {
            val linkString = matched.group(1)
            val linkUriOption = worthToFollowUri(linkString, uri)
            linkUriOption match {
              case Some(linkUri) =>
                context.parent ! CrawlerManager.ScanPage(linkUri, depth + 1)
              case None => //Do not follow the link.
            }
          }
      }.onComplete {
        case Success(done) =>
          //Source with data bytes read completely.
          context.parent !
            CrawlerManager.PageScanned(elapsedMillis, uri, depth)
          self ! PoisonPill
        case Failure(t) =>
          log.error(t, "GET request {} dataBytes read completed with Failure", uri)
          self ! PoisonPill
      }

    case HttpResponse(statusCode, headers, entity, _) if statusCode.isRedirection =>
      val locationString = headers.filter(_.is("location")).map(_.value).mkString
      log.debug("""GET request {} was redirected with status "{}" to {}""", uri, statusCode, locationString)
      val linkUriOption = worthToFollowUri(locationString, uri)
      linkUriOption match {
        case Some(linkUri) =>
          context.parent ! CrawlerManager.ScanPage(linkUri, depth + 1)
        case None => //Do not follow the link.
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
