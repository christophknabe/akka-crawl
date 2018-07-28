package de.bht_berlin.knabe.akkacrawl

import java.net.{URL, URLConnection}

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import com.typesafe.config.ConfigFactory
import javax.net.ssl.SSLException

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.io.StdIn

/**
 * Application to crawl the web and find as many working web page addresses as possible.
 * Detailed documentation see in the README file of the project.
 */
object AkkaCrawlApp extends App {

  val uriArg = args.headOption.getOrElse("https://www.berlin.de/")
  val uri = try { Uri(uriArg) } catch { case ex: Exception => throw new IllegalArgumentException(s"Malformed initial URL [$uriArg]", ex) }
  _adjustTrustStorePassword(uriArg)

  val system = ActorSystem("akka-crawl")
  val settings = Settings(system)
  //_printSystemProperties()
  println(s"Click into this window and press <ENTER> to start crawling from $uri")
  StdIn.readLine()

  val crawler = system.actorOf(Crawler.props(settings.responseTimeout).withMailbox("stoppable-mailbox"), name = "crawler")
  crawler ! Crawler.ScanPage(uri, 0)
  println("Press <ENTER> to stop crawling and print statistics!")
  StdIn.readLine()

  crawler ! Crawler.PrintScanSummary
  Await.result(system.whenTerminated, Duration.Inf)
  println("==========ActorSystem terminated. Exiting AkkaCrawlApp.==========")

  private def _printSystemProperties(): Unit = {
    val properties = System.getProperties.entrySet
    import scala.collection.JavaConverters._
    val entries = properties.asScala.toList.sortBy(_.getKey.toString)
    for(e <- entries){
      println(e)
    }
  }

  private def _adjustTrustStorePassword(uriArg: String): Unit = {
    val url = new URL(uriArg)
    val con: URLConnection = url.openConnection
    try{
      con.connect()
    }catch{
      case ssle: SSLException if ssle.getMessage == "java.lang.RuntimeException: Unexpected error: java.security.InvalidAlgorithmParameterException: the trustAnchors parameter must be non-empty" =>
        System.setProperty("javax.net.ssl.trustStorePassword", "changeit")
        //Now https access should work!
    }

  }

}
