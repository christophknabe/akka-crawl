package de.bht_berlin.knabe.akkacrawl

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.io.StdIn

object AkkaCrawlApp extends App {

  val uriArg = args.headOption.getOrElse("https://www.berlin.de/")
  val uri = try { Uri(uriArg) } catch { case ex: Exception => throw new IllegalArgumentException(s"Malformed initial URL [$uriArg]", ex) }

  val system = ActorSystem("akka-crawl")
  val settings = Settings(system)
  println(s"Click into this window and press <ENTER> to start crawling from $uri")
  StdIn.readLine()
  val crawlerManager = system.actorOf(CrawlerManager.props(settings.responseTimeout).withMailbox("stoppable-mailbox"), name="manager")
  crawlerManager ! CrawlerManager.ScanPage(uri, 0)
  println("Press <ENTER> to stop crawling and print statistics!")
  StdIn.readLine()
  crawlerManager ! CrawlerManager.PrintScanSummary
  Await.result(system.whenTerminated, Duration.Inf)
  println("==========ActorSystem terminated. Exiting AkkaCrawlApp.==========")

}
