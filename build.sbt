lazy val akkaCrawl = project.in(file("."))

name := "akka-crawl"

libraryDependencies ++= List(
  Library.akkaHttp,
  Library.scalaTest % "test"
)

initialCommands := """|import de.heikoseeberger.akkacrawl._""".stripMargin
