Global / scalaVersion := "2.13.16"

organization := "org.github.retronym.jacocotest"

lazy val demo = project
  .settings(
    libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value,
    libraryDependencies += "org.ow2.asm" % "asm-tree" % "9.7.1",
    libraryDependencies += "org.ow2.asm" % "asm-util" % "9.7.1",
    libraryDependencies += "org.jacoco" % "org.jacoco.core" % "0.8.12",
    libraryDependencies += "org.jacoco" % "org.jacoco.report" % "0.8.12",
    libraryDependencies += "junit" % "junit" % "4.13.2",
  )

