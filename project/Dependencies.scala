import sbt._

object Version {
  val scala = "2.13.2" //was "2.12.3"
  val akkaHttp = "10.1.8" //was "10.0.9"
  val akka = "2.5.25" //was "2.4.19"
  //val logback = "1.1.2"
  val scalaTest = "3.0.8" //was "3.0.3"
}

object Library {
  val akkaHttp = "com.typesafe.akka" %% "akka-http-core" % Version.akkaHttp
  val akkaStream = "com.typesafe.akka" %% "akka-stream" % Version.akka
  val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % Version.akka
  val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % Version.akka
  //val logbackClassic = "ch.qos.logback" % "logback-classic" % Version.logback
  val slf4jJdk14 = "org.slf4j" % "slf4j-jdk14" % "1.7.26" // java.util.logging works mostly out-of-the-box with SubstrateVM
  val scalaTest = "org.scalatest" %% "scalatest" % Version.scalaTest
}
