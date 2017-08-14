lazy val akkaCrawl = project.in(file("."))

name := "akka-crawl"

scalaVersion := Version.scala

libraryDependencies ++= List(
  Library.akkaHttp,
  Library.akkaSlf4j,
  Library.logbackClassic,
  Library.scalaTest % "test",
  "junit" % "junit" % "4.11" % "test"
)

