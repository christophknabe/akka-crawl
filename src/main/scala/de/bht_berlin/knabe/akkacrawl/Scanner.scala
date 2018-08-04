package de.bht_berlin.knabe.akkacrawl

import akka.actor.{Actor, ActorLogging, PoisonPill, Props, ReceiveTimeout, Status}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model.Uri.Path.{Empty, Segment, Slash}
import akka.http.scaladsl.model._
import akka.stream.scaladsl.Sink
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Materializer}

import scala.concurrent.duration.FiniteDuration
import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}

object Scanner {

  def props(url: Uri, responseTimeout: FiniteDuration, depth: Int = 0, materializer: Materializer) = Props(new Scanner(url, responseTimeout, depth, materializer))

  /** A RegEx pattern for recognizing links in a HTML page. */
  val linkPattern: Regex = """href="([^"]*)"""".r

  /**
    * Parses the uriString to a Uri.
    *
    * @return The parsed URI, resolved against the given baseUri, if it shows to a web page, which will probably contain more URIs. None otherwise or if an exception occured during parsing.
    *         Any fragment indications by # and any query parameters introduced by ? will be stripped off the returned Uri.
    */
  def worthToFollowUri(uriString: String, baseUri: Uri): Option[Uri] =
    for {
      completeUri <- Try(Uri.parseAndResolve(uriString, baseUri)).toOption
      resultUri = completeUri.copy(fragment = None, rawQueryString = None)
      if isSupportedScheme(resultUri.scheme)
      ext = extension(resultUri.path)
      if ext.toSet.subsetOf(worthyExtensions)
    } yield resultUri

  /**
    * Extracts the filename extension from the given path. An extension ends a path and starts with a period character.
    *
    * @return Some[String] containing the extension starting with its period character, if it exists. None otherwise.
    */
  @scala.annotation.tailrec
  private def extension(path: Path): Option[String] = path match {
    case Segment(_, Slash(tail)) => extension(tail)
    case Slash(tail) => extension(tail)
    case Segment(head, Empty) if head.contains(".") => head.split('.').lastOption
    case _ => None
  }

  private val isSupportedScheme = Set("http", "https")

  private val worthyExtensions = Set("html", "shtml", "jsp", "asp", "php")

}

/**
  * Scans the page at the URI, which has the given link depth.
  * When the page will be successfully scanned, a PageScanned command will be sent to the Crawler actor.
  * If in the page it encounters href-s to URIs, corresponding ScanPage commands will be sent to the Crawler in order to scan them, too.
  *
  * @param uri             address of the page to be scanned.
  * @param responseTimeout duration to wait for a HttpResponse before the page at the URI is considered as not retrievable.
  * @param depth           the link depth counted from the start URI.
  * @param materializer    a Materializer needed to materialize the streams from the HTTP responses
  */
class Scanner(uri: Uri, responseTimeout: FiniteDuration, depth: Int, implicit val materializer: Materializer)
  extends Actor
    with ActorLogging {

  import Scanner._
  import akka.pattern.pipe
  import context.dispatcher

  import scala.concurrent.duration._

  //TODO Will this create too many Http instances? If yes, move to object! 17-11-23
  private val http = Http(context.system)
  log.debug("Scanner for {} created", uri)

  private val startTime = System.currentTimeMillis

  private val parent = context.parent

  override def preStart(): Unit = {
    http.singleRequest(HttpRequest(uri = uri)).pipeTo(self)
    log.debug("Connecting to {} ...", uri.authority)
  }

  override def receive: Receive = {
    case HttpResponse(StatusCodes.OK, _, entity, _) =>
      log.debug("Getting page {} ...", uri)
      entity.dataBytes.runForeach {
        chunk =>
          //TODO Solve risk of breaking a URI by distribution to 2 adjacent chunks. Would need some buffering. 17-11-23
          for (matched <- linkPattern.findAllMatchIn(chunk.utf8String)) {
            val linkString = matched.group(1)
            for (linkUri <- worthToFollowUri(linkString, uri)) {
              parent ! Crawler.ScanPage(linkUri, depth + 1)
            }
          }
      }.onComplete {
        case Success(_) =>
          //Source with data bytes read completely.
          parent ! Crawler.PageScanned(elapsedMillis, uri, depth)
          self ! PoisonPill
        case Failure(t) =>
          log.error(t, "GET request {} dataBytes read completed with Failure", uri)
          self ! PoisonPill
      }

    case HttpResponse(statusCode, headers, entity, _) if statusCode.isRedirection =>
      val locationString = headers.filter(_.is("location")).map(_.value).mkString
      log.debug("""GET request {} was redirected with status "{}" to {}""", uri, statusCode, locationString)
      for (linkUri <- worthToFollowUri(locationString, uri)) {
        parent ! Crawler.ScanPage(linkUri, depth + 1)
      }
      entity.dataBytes.runWith(Sink.ignore).andThen{case _ => self ! PoisonPill}


    case HttpResponse(statusCode, _, entity, _) =>
      log.log(_level(depth), "GET request {} was answered with error {}", uri, statusCode)
      entity.dataBytes.runWith(Sink.ignore).andThen{case _ => self ! PoisonPill}
/*
    case ReceiveTimeout =>
      log.log(_level(depth), "GET request {} was not answered within {} millis.", uri, elapsedMillis)
      self ! PoisonPill
*/
    case Status.Failure(t) =>
      if (depth <= 0) {
        log.error(t, "GET request {} completed with following failure", uri)
      } else {
        log.debug("GET request {} completed with following failure:\n{}", uri, t)
      }
      self ! PoisonPill

    case unexpected =>
      log.error("GET request {} completed with unexpected {}", uri, unexpected)
      self ! PoisonPill
  }

  private def elapsedMillis = System.currentTimeMillis() - startTime

  private def _level(depth: Int): Logging.LogLevel = if (depth > 0) Logging.DebugLevel else Logging.ErrorLevel

}
