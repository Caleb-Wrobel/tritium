// io.tritium is deliberate: setun is the emulator substrate for the
// future tritium balanced ternary engine, which owns the org.
ThisBuild / organization := "io.tritium"
ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.7.1"

// Coverage rides Scala 3's built-in instrumentation (sbt-scoverage has
// no sbt 2 release yet): `sbt -Dcoverage=true test coverageReport`
// instruments main sources only and aggregates the modules' data into
// target/coverage-report/cobertura.xml (what CI hands to Codecov).
val coverageOn = sys.props.get("coverage").contains("true")

val commonSettings = Seq(
  libraryDependencies += "org.scalameta" %% "munit" % "1.1.1" % Test,
  scalacOptions ++= Seq("-deprecation", "-feature", "-Wunused:all"),
  Compile / compile / scalacOptions ++= (
    if coverageOn then Seq(s"-coverage-out:${(target.value / "coverage").getAbsolutePath}")
    else Nil
  )
)

lazy val core = (project in file("modules/core"))
  .settings(commonSettings)
  .settings(name := "setun-core")

lazy val machine = (project in file("modules/machine"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(name := "setun-machine")

lazy val coverageReport =
  taskKey[Unit]("Aggregate -coverage-out data into a Cobertura XML report")

lazy val root = (project in file("."))
  .aggregate(core, machine)
  .settings(
    name := "setun",
    publish / skip := true,
    // uncached: the measurement files written at test runtime are not
    // tracked task inputs, so the report must regenerate every run
    coverageReport := Def.uncached {
      import scoverage.reporter.{CoberturaXmlWriter, CoverageAggregator}
      val dataDirs = Seq((core / target).value, (machine / target).value)
        .map(_ / "coverage")
        .filter(_.exists)
      if dataDirs.isEmpty then
        sys.error("no coverage data; run `sbt -Dcoverage=true test coverageReport`")
      val sourceRoot = (ThisBuild / baseDirectory).value
      val coverage = CoverageAggregator
        .aggregate(dataDirs, sourceRoot)
        .getOrElse(sys.error(s"could not aggregate coverage data in $dataDirs"))
      val out = sourceRoot / "target" / "coverage-report"
      IO.createDirectory(out)
      new CoberturaXmlWriter(Seq(sourceRoot), out, None).write(coverage)
      streams.value.log.info(
        f"statement coverage ${coverage.statementCoveragePercent}%.2f%% -> ${out / "cobertura.xml"}"
      )
    }
  )
