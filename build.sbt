
name := "TreeUtils"

version := "1.5"

scalaVersion := "2.13.5"

resolvers += Resolver.sonatypeRepo("public")
resolvers += Resolver.sonatypeRepo("releases")

libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.9.3"

libraryDependencies += "org.typelevel" %% "discipline-scalatest" % "2.1.4" % Test

libraryDependencies += "org.typelevel" %% "discipline-scalatest" % "2.1.4"

libraryDependencies += "com.google.code.gson" % "gson" % "2.8.6"

libraryDependencies += "info.picocli" % "picocli" % "3.8.1"

resolvers += Resolver.bintrayRepo("commercetools", "maven")

// libraryDependencies += "io.sphere" %% "sphere-util" % "0.12.1"
// libraryDependencies += "io.sphere" %% "sphere-json" % "0.12.1"
// libraryDependencies += "io.sphere" %% "sphere-mongo" % "0.12.1"

// scalacOptions += "-target:jvm-1.7"

// set the main class for packaging the main jar
// 'run' will still auto-detect and prompt
// change Compile to Test to set it for the test jar
mainClass in (Compile, packageBin) := Some("main.scala.MixMain")

// set the main class for the main 'run' task
// change Compile to Test to set it for 'test:run'
mainClass in (Compile, run) := Some("main.scala.MixMain")

//scalaHome := Some(file("/Users/aaronmck/scala-2.10.3/"))
