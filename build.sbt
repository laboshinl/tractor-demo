name := "tractor-demo"

version := "1.0"

scalaVersion := "2.11.8"

lazy val akkaVersion = "2.4.6"

fork in Test := true

mainClass in assembly := Some("ru.laboshinl.tractor.ApplicationMain")

assemblyJarName in assembly := "tractor.jar"

test in assembly := {}

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
  "org.apache.commons" % "commons-math3" % "3.6.1",
  "org.spire-math" % "spire_2.11" % "0.12.0",
  "com.twitter" % "chill-akka_2.11" % "0.8.1"
  /*"com.github.pathikrit" %% "better-files" % "2.16.0"*/
)


javaCppPresetLibs ++= Seq("tensorflow" -> "0.9.0")

resolvers += Resolver.sonatypeRepo("snapshots")

fork in run := true

classpathTypes += "maven-plugin"