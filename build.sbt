import zio.sbt.githubactions.Condition

val V = new {
  val brotli = "0.1.2"
  val brotli4j = "1.23.0"
  val commonsCompress = "1.28.0"
  val logbackClassic = "1.5.32"
  val lz4 = "1.11.0"
  val snappy = "1.1.10.8"
  val zio = "2.1.26"
  val zip4j = "2.11.6"
  val zstdJni = "1.5.7-8"
}

val _scala212 = "2.12.21"
val _scala213 = "2.13.18"
val _scala3 = "3.3.7"
val scalaVersions = List(_scala3, _scala213, _scala212)

enablePlugins(ZioSbtEcosystemPlugin, ZioSbtCiPlugin)

inThisBuild(
  List(
    name := "ZIO Streams Compress",
    // zio-sbt defines these 'scala*' settings, we redefine them here for better control
    scala212 := _scala212,
    scala213 := _scala213,
    scala3 := _scala3,
    crossScalaVersions := List(scala3.value, scala213.value, scala212.value),
    run / fork := true,
    // Update the readme on every push to master:
    ciUpdateReadmeCondition := Some(
      Condition.Expression("github.ref == format('refs/heads/{0}', github.event.repository.default_branch)")
    ),
    ciEnabledBranches := Seq("main"),
    ciTargetJavaVersions := List("17", "21", "25"),
    ciJvmOptions ++= Seq("-Xmx4G", "-Xss2M", "-XX:+UseG1GC"),
    versionScheme := Some("early-semver"),
    developers := List(
      Developer(
        "erikvanoosten",
        "Erik van Oosten",
        "",
        url("https://github.com/erikvanoosten"),
      )
    ),
    // Needed for scalafix:
    semanticdbEnabled := Keys.scalaBinaryVersion.value != "3",
    // semanticdbOptions += "-P:semanticdb:synthetics:on", // Causes errors during lint
    semanticdbVersion := scalafixSemanticdb.revision,
    scalafixDependencies ++= List(
      "com.github.vovapolu" %% "scaluzzi" % "0.1.23",
      "io.github.ghostbuster91.scalafix-unified" %% "unified" % "0.0.9",
    ),
  )
)

def commonSettings(projectName: String) = Seq(
  name := s"zio-streams-compress-$projectName",
  libraryDependencies ++= Seq(
    "dev.zio" %% "zio-test" % V.zio % Test,
    "dev.zio" %% "zio-test-sbt" % V.zio % Test,
    "ch.qos.logback" % "logback-classic" % V.logbackClassic % Test,
  ),
  libraryDependencies ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) => Seq(compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"))
      case _            => List.empty
    }
  },
)

lazy val root =
  project
    .in(file("."))
    .settings(
      name := "zio-streams-compress",
      publish / skip := true,
      crossScalaVersions :=
        Nil, // https://www.scala-sbt.org/1.x/docs/Cross-Build.html#Cross+building+a+project+statefully,
      publishArtifact := false,
    )
    .aggregate(
      core,
      brotli,
      brotli4j,
      bzip2,
      gzip,
      lz4,
      snappy,
      tar,
      zip,
      zip4j,
      zstd,
      example,
      docs,
    )

lazy val core = project
  .in(file("core"))
  .settings(commonSettings("core"))
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-streams" % V.zio
    )
  )

lazy val brotli = project
  .in(file("brotli"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(commonSettings("brotli"))
  .settings(
    libraryDependencies ++= Seq(
      "org.brotli" % "dec" % V.brotli
    )
  )

lazy val brotli4j = project
  .in(file("brotli4j"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(commonSettings("brotli4j"))
  .settings(
    libraryDependencies ++= Seq(
      "com.aayushatharva.brotli4j" % "brotli4j" % V.brotli4j
    )
  )

lazy val bzip2 = project
  .in(file("bzip2"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(commonSettings("bzip2"))
  .settings(
    libraryDependencies ++= Seq(
      "org.apache.commons" % "commons-compress" % V.commonsCompress
    )
  )

lazy val gzip = project
  .in(file("gzip"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(commonSettings("gzip"))

lazy val lz4 = project
  .in(file("lz4"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(commonSettings("lz4"))
  .settings(
    name := "zio-streams-compress-lz4",
    libraryDependencies ++= Seq(
      "at.yawk.lz4" % "lz4-java" % V.lz4
    ),
  )

lazy val snappy = project
  .in(file("snappy"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(commonSettings("snappy"))
  .settings(
    name := "zio-streams-compress-snappy",
    libraryDependencies ++= Seq(
      "org.xerial.snappy" % "snappy-java" % V.snappy
    ),
  )

lazy val tar = project
  .in(file("tar"))
  .dependsOn(core % "compile->compile;test->test")
  .dependsOn(gzip % "test")
  .settings(commonSettings("tar"))
  .settings(
    libraryDependencies ++= Seq(
      "org.apache.commons" % "commons-compress" % V.commonsCompress
    )
  )

lazy val zip = project
  .in(file("zip"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(commonSettings("zip"))

lazy val zip4j = project
  .in(file("zip4j"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(commonSettings("zip4j"))
  .settings(
    libraryDependencies ++= Seq(
      "net.lingala.zip4j" % "zip4j" % V.zip4j
    )
  )

lazy val zstd = project
  .in(file("zstd"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(commonSettings("zstd"))
  .settings(
    libraryDependencies ++= Seq(
      "com.github.luben" % "zstd-jni" % V.zstdJni
    )
  )

lazy val example = project
  .in(file("example"))
  .dependsOn(gzip, tar, zip4j)
  .settings(commonSettings("example"))
  .settings(
    publishArtifact := false,
    publish / skip := true,
  )
  .settings(
    name := "zio-streams-compress-example"
  )

lazy val docs = project
  .in(file("docs-project"))
  .dependsOn(core)
  .enablePlugins(WebsitePlugin)
  .settings(commonSettings("docs"))
  .settings(
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    crossScalaVersions := List(_scala213),
    projectName := "ZIO Streams Compress docs",
    mainModuleName := "zio-streams-compress-docs",
    projectStage := ProjectStage.ProductionReady,
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(core),
    readmeCredits :=
      "This library is heavily inspired by [fs2-compress](https://github.com/lhns/fs2-compress).",
    readmeLicense +=
      s"\n\nCopyright 2024-${java.time.Year.now()} Erik van Oosten and the zio-streams-compress contributors.",
  )
