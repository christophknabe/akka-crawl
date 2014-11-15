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

  @Test def vergleich {
    val result = Crawler.linkPattern.findFirstMatchIn("""abc"http://spray.io/documentation/1.2.2/spray-can/"def""")
    result match {
      case Some(matched) => expect("""http://spray.io/documentation/1.2.2/spray-can/"""){matched.group(1)}
      //OK
    }
  }

}// class FacultyTest

object CrawlerTest {

  @BeforeClass
  def beforeClass(): Unit = {
    println("beforeClass")
  }

}