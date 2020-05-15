lazy val akkaCrawl = project.in(file("."))

name := "akka-crawl"

scalaVersion := Version.scala

logLevel := Level.Debug

val graalAkkaVersion = "0.5.0"
libraryDependencies ++= List(
  Library.akkaHttp,
  Library.akkaStream,
  Library.akkaSlf4j,
  //Library.logbackClassic,
  Library.slf4jJdk14,
  Library.scalaTest % "test",
  "junit" % "junit" % "4.11" % "test",
  "com.github.vmencik" %% "graal-akka-http" % graalAkkaVersion,
  "com.github.vmencik" %% "graal-akka-slf4j" % graalAkkaVersion
)

enablePlugins(GraalVMNativeImagePlugin)

graalVMNativeImageOptions ++= Seq(
  "-H:IncludeResources=.*\\.properties",
  s"-H:ReflectionConfigurationFiles=${baseDirectory.value}/graal/reflectconf-jul.json,${baseDirectory.value}/graal/reflectconf-akkacrawl.json",
  "--initialize-at-build-time",
  "--initialize-at-run-time=" +
    "akka.protobuf.DescriptorProtos," +
    "com.typesafe.config.impl.ConfigImpl$EnvVariablesHolder," +
    "com.typesafe.config.impl.ConfigImpl$SystemPropertiesHolder",
  "--no-fallback",
  "--allow-incomplete-classpath"
  //2020-05-14 Knabe:
  , "--report-unsupported-elements-at-runtime"
)

