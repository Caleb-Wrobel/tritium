// sbt-scoverage has no sbt 2 release yet, so coverage uses Scala 3's
// built-in instrumentation (-coverage-out); this reporter library turns
// the emitted data into a Cobertura XML report (see coverageReport in
// build.sbt).
libraryDependencies += "org.scoverage" %% "scalac-scoverage-reporter" % "2.3.0"
