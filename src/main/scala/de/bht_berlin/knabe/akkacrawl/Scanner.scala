package de.bht_berlin.knabe.akkacrawl

import akka.actor.{ Actor, ActorLogging, PoisonPill, Props, ReceiveTimeout }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model.Uri.Path.{ Empty, Segment, Slash }
import akka.http.scaladsl.model._
import akka.stream.scaladsl.Sink
import akka.stream.{ ActorMaterializer, ActorMaterializerSettings }

import scala.concurrent.duration.FiniteDuration
import scala.util.{ Failure, Success, Try }

object Scanner {

  def props(url: Uri, responseTimeout: FiniteDuration, depth: Int = 0) = Props(new Scanner(url, responseTimeout, depth))

  /** A RegEx pattern for recognizing links in a HTML page. */
  val linkPattern =
    """href="([^"]*)"""".r

  /**
   * Parses the uriString to a Uri.
   *
   * @return The parsed URI, resolved against the given baseUri, if it shows to a web page, which will probably contain more URIs. None otherwise or if an exception occured during parsing.
   *         Any fragment indications by # and any query parameters introduced by ? will be stripped off the returned Uri.
   */
  def worthToFollowUri(uriString: String, baseUri: Uri): Option[Uri] =
    for {
      completeUri <- Try(Uri.parseAndResolve(uriString, baseUri)).toOption
      resultUri = completeUri.copy(fragment       = None, rawQueryString = None)
      if isSupportedScheme(resultUri.scheme)
      ext = extension(resultUri.path)
      if ext.toSet.subsetOf(worthyExtensions)
    } yield resultUri

  @scala.annotation.tailrec
  private def extension(path: Path): Option[String] =
    path match {
      case Segment(_, Slash(tail))                    => extension(tail)
      case Slash(tail)                                => extension(tail)
      case Segment(head, Empty) if head.contains(".") => head.split('.').lastOption
      case _                                          => None
    }

  private val isSupportedScheme = Set("http", "https")

  private val worthyExtensions = Set("html", "shtml", "jsp", "asp", "php")
}

/**
 * Scans the page at the URI, which has the given link depth.
 * When the page will be successfully scanned, a PageScanned command will be sent to the Crawler actor.
 * If in the page it encounters href-s to URIs, corresponding ScanPage commands will be sent to the Crawler in order to scan them, too.
 *
 * @param uri address of the page to be scanned.
 * @param responseTimeout duration to wait for a HttpResponse before the page at the URI is considered as not retrievable.
 * @param depth the link depth counted from the start URI.
 */
class Scanner(uri: Uri, responseTimeout: FiniteDuration, depth: Int)
  extends Actor
  with ActorLogging {

  import Scanner._
  import akka.pattern.pipe
  import context.dispatcher

  import scala.concurrent.duration._

  private final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

  private val http = Http(context.system)
  log.debug("Scanner for {} created", uri)

  private val startTime = System.currentTimeMillis

  override def preStart(): Unit = {
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
                context.parent ! Crawler.ScanPage(linkUri, depth + 1)
              case None => //Do not follow the link.
            }
          }
      }.onComplete {
        case Success(done) =>
          //Source with data bytes read completely.
          context.parent ! Crawler.PageScanned(elapsedMillis, uri, depth)
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
          context.parent ! Crawler.ScanPage(linkUri, depth + 1)
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
