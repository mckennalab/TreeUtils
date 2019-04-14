name := "TreeUtils"

version := "1.1"

scalaVersion := "2.11.7"

resolvers += Resolver.sonatypeRepo("public")

libraryDependencies += "org.scalatest" % "scalatest_2.11" % "2.2.4" % "test"

libraryDependencies += "org.scalatest" % "scalatest_2.11" % "2.2.4"

libraryDependencies += "com.google.code.gson" % "gson" % "2.6.2"

libraryDependencies += "com.github.scopt" %% "scopt" % "3.7.0"

resolvers += Resolver.bintrayRepo("commercetools", "maven")

libraryDependencies += "io.sphere" %% "sphere-util" % "0.9.17"
libraryDependencies += "io.sphere" %% "sphere-json" % "0.9.17"
libraryDependencies += "io.sphere" %% "sphere-mongo" % "0.9.17"

scalacOptions += "-target:jvm-1.7"

// set the main class for packaging the main jar
// 'run' will still auto-detect and prompt
// change Compile to Test to set it for the test jar
mainClass in (Compile, packageBin) := Some("main.scala.MixMain")

// set the main class for the main 'run' task
// change Compile to Test to set it for 'test:run'
mainClass in (Compile, run) := Some("main.scala.MixMain")

//scalaHome := Some(file("/Users/aaronmck/scala-2.10.3/"))
