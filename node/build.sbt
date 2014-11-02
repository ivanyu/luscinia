name := "luscinia-node"

version := "1.0"

scalaVersion := "2.11.4"

resolvers ++= Seq(
  "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
  "spray repo" at "http://repo.spray.io"
)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.6",
  "com.typesafe" % "config" % "1.2.1",
  "io.spray" % "spray-can_2.11" % "1.3.2",
  "io.spray" % "spray-routing_2.11" % "1.3.2"
)