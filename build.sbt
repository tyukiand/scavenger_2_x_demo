// This file describes your project, specifies the dependencies, 
// and tells SBT what to build in the end.
lazy val root = (project in file(".")).
  settings(
    // Some information about your project (name, version...)
    organization := "org.foobar",
    name := "scavenger_demo",
    version := "3.14",
    // Scala version used in your project, list of dependencies
    scalaVersion := "2.10.4",
    libraryDependencies ++= Seq(
      "org.scavenger" % "scavenger_2.10" % "2.1"
    ),
    // collect all dependency-jars into single folder,
    // useful if you want to copy all this stuff to the cluster at once
    // (requires sbt-pack plugin, that is, you have to add the line
    // addSbtPlugin("org.xerial.sbt" % "sbt-pack" % "0.7.5")
    // into ~/.sbt/0.13/plugins/plugins.sbt 
    // and restart sbt
    packAutoSettings
  )
