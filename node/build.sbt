import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

name := "luscinia-node"

version := "1.0"

scalaVersion := "2.10.4"

resolvers ++= Seq(
//  "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
  "Akka snapshot repo"  at "http://repo.akka.io/snapshots/",
  "Spray Repository"    at "http://repo.spray.io/"
)

val akkaVersion = "2.4-SNAPSHOT"

val sprayVersion = "1.3.2"

libraryDependencies ++= Seq(
//  "com.typesafe.akka" %% "akka-actor" % "2.3.6",
//  "com.typesafe.akka" %% "akka-testkit" % "2.3.6" % "test",
  "com.typesafe.akka" %% "akka-actor"   % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test",
  "com.typesafe" % "config" % "1.2.1",
  "io.spray" %% "spray-can"     % sprayVersion,
  "io.spray" %% "spray-routing" % sprayVersion,
  "io.spray" %% "spray-client"  % sprayVersion,
  "com.wandoulabs.akka" %% "spray-websocket" % "0.1.3",
  "org.scala-lang" % "scala-reflect" % scalaVersion.value
)

scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-encoding", "UTF-8"
)
