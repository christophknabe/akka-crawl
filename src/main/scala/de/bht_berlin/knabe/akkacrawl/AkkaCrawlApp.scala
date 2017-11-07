package de.bht_berlin.knabe.akkacrawl

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.io.StdIn

/**Application to crawl the web and find as many working web page addresses as possible.
  * Detailed documentation see in the README file of the project.*/
object AkkaCrawlApp extends App {

  val uriArg = args.headOption.getOrElse("https://www.berlin.de/")
  val uri = try { Uri(uriArg) } catch { case ex: Exception => throw new IllegalArgumentException(s"Malformed initial URL [$uriArg]", ex) }

  val system = ActorSystem("akka-crawl")
  val settings = Settings(system)
  println(s"Click into this window and press <ENTER> to start crawling from $uri")
  StdIn.readLine()
  val crawler = system.actorOf(Crawler.props(settings.responseTimeout).withMailbox("stoppable-mailbox"), name = "crawler")
  crawler ! Crawler.ScanPage(uri, 0)
  println("Press <ENTER> to stop crawling and print statistics!")
  StdIn.readLine()
  crawler ! Crawler.PrintScanSummary
  Await.result(system.whenTerminated, Duration.Inf)
  println("==========ActorSystem terminated. Exiting AkkaCrawlApp.==========")

}
