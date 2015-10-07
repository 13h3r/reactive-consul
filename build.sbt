name := "reactive-consul"

organization := "com.13h3r"

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  "com.typesafe.akka" % "akka-stream-experimental_2.11" % "1.0"
  , "com.typesafe.akka" %% "akka-http-core-experimental" % "1.0"
  , "com.typesafe.akka" %% "akka-http-spray-json-experimental" % "1.0"
  , "io.spray" %%  "spray-json" % "1.3.2"
)
