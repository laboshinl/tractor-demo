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
  "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "com.typesafe.akka" %% "akka-agent" % akkaVersion,
  "com.typesafe.akka" %% "akka-contrib" % akkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.0.9",
  "com.github.romix.akka" %% "akka-kryo-serialization" % "0.4.1",
  "org.apache.commons" % "commons-math3" % "3.6.1",
  "org.scalaz" %% "scalaz-core" % "7.2.6",
  "com.github.romix.akka" %% "akka-kryo-serialization" % "0.4.1",
  "org.spire-math" % "spire_2.11" % "0.12.0"
  )
