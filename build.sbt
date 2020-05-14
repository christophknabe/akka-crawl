lazy val akkaCrawl = project.in(file("."))

name := "akka-crawl"

scalaVersion := Version.scala

logLevel := Level.Debug

libraryDependencies ++= List(
  Library.akkaHttp,
  Library.akkaStream,
  Library.akkaSlf4j,
  Library.logbackClassic,
  Library.scalaTest % "test",
  "junit" % "junit" % "4.11" % "test"
)

