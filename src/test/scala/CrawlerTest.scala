import java.net.URL

import de.heikoseeberger.akkacrawl.Crawler
import org.junit.{BeforeClass, Test, Before}
import org.junit.Assert._
import org.junit.Test
import org.scalatest.junit.JUnitSuite

/**
 * Created by knabe on 15.11.14.
 */
class CrawlerTest extends JUnitSuite {

  /**Deletes test Subscriptions and test Users before each test method.*/
  @Before
  def beforeMethod(): Unit = println("beforeMethod")

  @Test def linkPattern(){
    val url: String = """abc"http://spray.io/documentation/1.2.2/spray-can/"def"""
    val result = Crawler.linkPattern.findFirstMatchIn(url)
    result match {
      case Some(matched) => assertResult("""http://spray.io/documentation/1.2.2/spray-can/"""){matched.group(1)}
      case None => fail(s"Should find URL match in: $url")
    }
  }

  @Test def  isWorthToFollow(){
    assertResult(true){Crawler.isWorthToFollow(new URL("http://www.kicker.de"))}
    assertResult(true){Crawler.isWorthToFollow(new URL("http://spray.io/documentation/1.2.2/spray-can"))}
    assertResult(true){Crawler.isWorthToFollow(new URL("http://spray.io/documentation/1.2.2/spray-can/"))}
    assertResult(false){Crawler.isWorthToFollow(new URL("http://mediadb.kicker.de/special/facebook/images/logo-kicker.png"))}
    assertResult(false){Crawler.isWorthToFollow(new URL("http://voting.kicker.de/generic/js/general_7.68.5430.30309.js"))}
    assertResult(false){Crawler.isWorthToFollow(new URL("http://www.kicker.de/search.xml"))}
  }

}// class FacultyTest

object CrawlerTest {

  @BeforeClass
  def beforeClass(): Unit = {
    println("beforeClass")
  }

}