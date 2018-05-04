name := "tractor-demo"

version := "1.0"

scalaVersion := "2.11.8"

lazy val akkaVersion = "2.4.6"

fork in Test := true

mainClass in assembly := Some("ru.laboshinl.tractor.ApplicationMain")

assemblyJarName in assembly := "tractor.jar"

assemblyMergeStrategy in assembly := {
   case PathList("reference.conf") => MergeStrategy.concat
   case PathList("META-INF", xs @ _*) => MergeStrategy.discard
   case x => MergeStrategy.first
}

test in assembly := {}

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
  "org.apache.commons" % "commons-math3" % "3.6.1",
  "org.spire-math" % "spire_2.11" % "0.12.0",
  "com.twitter" % "chill-akka_2.11" % "0.8.1",
  "com.github.pathikrit" %% "better-files" % "2.16.0",
  "org.apache.commons" % "commons-lang3" % "3.5",
  "com.github.tototoshi" %% "scala-csv" % "1.3.3",
//"edu.berkeley.compbio"% "jlibsvm" %"0.911",
  "nz.ac.waikato.cms.weka" % "weka-stable" % "3.8.0"
)

//javaCppPresetLibs ++= Seq("tensorflow" -> "0.9.0")

//resolvers += "dev.davidsoergel.com releases" at "http://dev.davidsoergel.com/nexus/content/repositories/releases"

//resolvers += "dev.davidsoergel.com third" at "http://davidsoergel.com/nexus/content/repositories/thirdparty"

//resolvers += Resolver.sonatypeRepo("snapshots")

fork in run := true

//classpathTypes += "maven-plugin"
