package de.bht_berlin.knabe.akkacrawl

import org.junit.{BeforeClass, Test, Before}
import org.scalatest.junit.JUnitSuite

/**
 * Created by knabe on 15.11.14.
 */
class CrawlerTest extends JUnitSuite {

  private val hostPath = "://spray.io/documentation/1.2.2/spray-can/"

  @Test def httpLinkPattern(){
    val text = "abc\"http" + hostPath + "\"def"
    val result = Crawler.linkPattern.findFirstMatchIn(text)
    result match {
      case Some(matched) => assertResult("http"+hostPath){matched.group(1)}
      case None => fail(s"Should find URL match in: $text")
    }
  }
  @Test def httpsLinkPattern(){
    val text = "abc\"https" + hostPath + "\"def"
    val result = Crawler.linkPattern.findFirstMatchIn(text)
    result match {
      case Some(matched) => assertResult("https"+hostPath){matched.group(1)}
      case None => fail(s"Should find URL match in: $text")
    }
  }

  @Test def  isWorthToFollow(){
    import akka.http.scaladsl.model.Uri
    {
      val uri = Uri("http://www.kicker.de")
      assertResult(true, uri){Crawler.isWorthToFollow(uri)}
    }
    {
      val uri = Uri("http://www.kicker.de/")
      assertResult(true, uri){Crawler.isWorthToFollow(uri)}
    }
    {
      val uri = Uri("http://spray.io/documentation/1.2.2/spray-can")
      assertResult(true, uri){Crawler.isWorthToFollow(uri)}
    }
    {
      val uri = Uri("http://spray.io/documentation/1.2.2/spray-can/")
      assertResult(true, uri){Crawler.isWorthToFollow(uri)}
    }
    {
      val uri = Uri("http://mediadb.kicker.de/special/facebook/images/logo-kicker.png")
      assertResult(false, uri){Crawler.isWorthToFollow(uri)}
    }
    {
      val uri = Uri("http://voting.kicker.de/generic/js/general_7.68.5430.30309.js")
      assertResult(false, uri){
        Crawler.isWorthToFollow(uri)}
    }
    {
      val uri = Uri("http://www.kicker.de/search.xml")
      assertResult(false, uri){Crawler.isWorthToFollow(uri)}
    }
    {
      val uri = Uri("https://www.kicker.de")
      assertResult(true, uri){Crawler.isWorthToFollow(uri)}
    }
  }

}// class CrawlerTest
