lazy val akkaCrawl = project.in(file("."))

name := "akka-crawl"

libraryDependencies ++= List(
)

initialCommands := """|import de.heikoseeberger.akkacrawl._""".stripMargin
