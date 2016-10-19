name := "tractor-demo"

version := "1.0"

scalaVersion := "2.11.8"

lazy val akkaVersion = "2.4.6"

fork in Test := true

mainClass in assembly := Some("ru.laboshinl.tractor.ApplicationMain")

assemblyJarName in assembly := "tractor.jar"

test in assembly := {}

//lazy val tensorVersion = "0.9.0-1.2"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
  "com.github.romix.akka" %% "akka-kryo-serialization" % "0.4.1",
  "org.apache.commons" % "commons-math3" % "3.6.1",
  "org.spire-math" % "spire_2.11" % "0.12.0",
  "com.github.pathikrit" %% "better-files" % "2.16.0"
  //"org.bytedeco.javacpp-presets" % "tensorflow" % "0.8.0-1.2",
// "org.bytedeco.javacpp-presets" % "tensorflow" % tensorVersion classifier "linux-x86_64",
//  "org.bytedeco.javacpp-presets" % "tensorflow" % "0.10.0-1.2.5-SNAPSHOT"
//  "org.bytedeco" % "javacpp" % "1.2.4"
)

//resolvers += "javacpp" at "https://people.eecs.berkeley.edu/~rkn/snapshot-2016-03-16-CPU/"

//libraryDependencies += "org.bytedeco" % "javacpp" % "1.2-SPARKNETCPU"

//libraryDependencies += "org.bytedeco.javacpp-presets" % "tensorflow" % "master-1.2-SPARKNETCPU"

//libraryDependencies += "org.bytedeco.javacpp-presets" % "tensorflow" % "master-1.2-SPARKNETCPU" classifier "linux-x86_64"

javaCppPresetLibs ++= Seq("tensorflow" -> "0.9.0")

resolvers += Resolver.sonatypeRepo("snapshots")

fork in run := true

classpathTypes += "maven-plugin"