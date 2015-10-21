import casino.Release._

name := "reactive-consul"

organization := "com.13h3r"

scalaVersion := "2.11.7"

librarySettings

libraryDependencies ++= Seq(
//  "com.typesafe.akka" %% "akka-actor" % "2.3.14"
  "com.typesafe.akka" %% "akka-stream-experimental" % "1.0"
  , "com.typesafe.akka" %% "akka-http-core-experimental" % "1.0"
  , "com.typesafe.akka" %% "akka-http-spray-json-experimental" % "1.0"
  , "io.spray" %%  "spray-json" % "1.3.2"
)
