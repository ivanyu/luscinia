name := "luscinia-node"

version := "1.0"

scalaVersion := "2.10.4"

resolvers ++= Seq(
//  "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
  "Akka snapshot repo"  at "http://repo.akka.io/snapshots/",
  "Spray Repository"    at "http://repo.spray.io/"
)

libraryDependencies ++= Seq(
//  "com.typesafe.akka" %% "akka-actor" % "2.3.6",
//  "com.typesafe.akka" %% "akka-testkit" % "2.3.6" % "test",
  "com.typesafe.akka" %% "akka-actor" % "2.4-20141121-230846",
  "com.typesafe.akka" %% "akka-testkit" % "2.4-20141121-230846" % "test",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test",
  "com.typesafe" % "config" % "1.2.1",
  "io.spray" %% "spray-can"     % "1.3.2",
  "io.spray" %% "spray-routing" % "1.3.2",
  "io.spray" %% "spray-client"  % "1.3.2",
  "com.wandoulabs.akka" %% "spray-websocket" % "0.1.3",
  "org.scala-lang" % "scala-reflect" % scalaVersion.value
)

scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-encoding", "UTF-8"
)
