// io.tritium is deliberate: setun is the emulator substrate for the
// future tritium balanced ternary engine, which owns the org.
ThisBuild / organization := "io.tritium"
ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.7.1"

lazy val core = (project in file("modules/core"))
  .settings(
    name := "setun-core",
    libraryDependencies += "org.scalameta" %% "munit" % "1.1.1" % Test,
    scalacOptions ++= Seq("-deprecation", "-feature", "-Wunused:all")
  )

lazy val machine = (project in file("modules/machine"))
  .dependsOn(core)
  .settings(
    name := "setun-machine",
    libraryDependencies += "org.scalameta" %% "munit" % "1.1.1" % Test,
    scalacOptions ++= Seq("-deprecation", "-feature", "-Wunused:all")
  )

lazy val root = (project in file("."))
  .aggregate(core, machine)
  .settings(
    name := "setun",
    publish / skip := true
  )
