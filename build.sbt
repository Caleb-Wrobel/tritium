ThisBuild / organization := "io.tritium"
ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.7.1"

lazy val core = (project in file("modules/core"))
  .settings(
    name := "tritium-core",
    libraryDependencies += "org.scalameta" %% "munit" % "1.1.1" % Test,
    scalacOptions ++= Seq("-deprecation", "-feature", "-Wunused:all")
  )

lazy val machine = (project in file("modules/machine"))
  .dependsOn(core)
  .settings(
    name := "tritium-machine",
    libraryDependencies += "org.scalameta" %% "munit" % "1.1.1" % Test,
    scalacOptions ++= Seq("-deprecation", "-feature", "-Wunused:all")
  )

lazy val root = (project in file("."))
  .aggregate(core, machine)
  .settings(
    name := "tritium",
    publish / skip := true
  )
