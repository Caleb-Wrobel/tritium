// io.tritium is deliberate: setun is the emulator substrate for the
// future tritium balanced ternary engine, which owns the org.
ThisBuild / organization := "io.tritium"
ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.7.1"

// Coverage rides Scala 3's built-in instrumentation (sbt-scoverage has
// no sbt 2 release yet): -Dcoverage=true instruments main sources, and
// root/coverageReport aggregates the modules' data into
// target/coverage-report/cobertura.xml (what CI hands to Codecov).
//
// The data files are side effects sbt's build cache can't see, so a
// cache-hit compile/test silently produces none: coverage runs need a
// cold build cache and a fresh target (and `clean` is NOT a substitute
// — sbt restores compile outputs without re-emitting the data). Local
// recipe:
//
//   sbt shutdown; rm -rf target
//   sbt --server --batch --sbt-cache "$(mktemp -d)" -Dcoverage=true \
//     "testFull; coverageReport"
val coverageOn = sys.props.get("coverage").contains("true")

val commonSettings = Seq(
  libraryDependencies += "org.scalameta" %% "munit" % "1.1.1" % Test,
  scalacOptions ++= Seq("-deprecation", "-feature", "-Wunused:all"),
  Compile / compile / scalacOptions ++= (
    if coverageOn then Seq(s"-coverage-out:${(target.value / "coverage").getAbsolutePath}")
    else Nil
  ),
  // the coverage Invoker dedupes measurement writes per JVM, so tests
  // must not run inside the long-lived sbt server (a second run there
  // would record nothing after clean wiped the data files)
  Test / fork := coverageOn
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
