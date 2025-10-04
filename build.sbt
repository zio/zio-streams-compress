val V = new {
  val brotli = "0.1.2"
  val brotli4j = "1.20.0"
  val commonsCompress = "1.28.0"
  val logbackClassic = "1.5.19"
  val lz4 = "1.8.0"
  val snappy = "1.1.10.8"
  val zio = "2.1.21"
  val zip4j = "2.11.5"
  val zstdJni = "1.5.7-5"
}

val _scala212 = "2.12.20"
val _scala213 = "2.13.16"
val _scala3 = "3.3.6"
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
    ciEnabledBranches := Seq("main"),
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
    "dev.zio" %%% "zio-test" % V.zio % Test,
    "dev.zio" %%% "zio-test-sbt" % V.zio % Test,
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
      crossScalaVersions := Nil, // https://www.scala-sbt.org/1.x/docs/Cross-Build.html#Cross+building+a+project+statefully,
      publishArtifact := false,
    )
    .aggregate(core.projectRefs: _*)
    .aggregate(brotli.projectRefs: _*)
    .aggregate(brotli4j.projectRefs: _*)
    .aggregate(bzip2.projectRefs: _*)
    .aggregate(gzip.projectRefs: _*)
    .aggregate(lz4.projectRefs: _*)
    .aggregate(snappy.projectRefs: _*)
    .aggregate(tar.projectRefs: _*)
    .aggregate(zip.projectRefs: _*)
    .aggregate(zip4j.projectRefs: _*)
    .aggregate(zstd.projectRefs: _*)
    .aggregate(example.projectRefs: _*)
    .aggregate(docs)

lazy val core = projectMatrix
  .in(file("core"))
  .settings(commonSettings("core"))
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-streams" % V.zio
    )
  )
  .jvmPlatform(scalaVersions)
  .jsPlatform(scalaVersions)

lazy val brotli = projectMatrix
  .in(file("brotli"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(commonSettings("brotli"))
  .settings(
    libraryDependencies ++= Seq(
      "org.brotli" % "dec" % V.brotli
    )
  )
  .jvmPlatform(scalaVersions)

lazy val brotli4j = projectMatrix
  .in(file("brotli4j"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(commonSettings("brotli4j"))
  .settings(
    libraryDependencies ++= Seq(
      "com.aayushatharva.brotli4j" % "brotli4j" % V.brotli4j
    )
  )
  .jvmPlatform(scalaVersions)

lazy val bzip2 = projectMatrix
  .in(file("bzip2"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(commonSettings("bzip2"))
  .settings(
    libraryDependencies ++= Seq(
      "org.apache.commons" % "commons-compress" % V.commonsCompress
    )
  )
  .jvmPlatform(scalaVersions)

lazy val gzip = projectMatrix
  .in(file("gzip"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(commonSettings("gzip"))
  .jvmPlatform(scalaVersions)
//.jsPlatform(scalaVersions)

lazy val lz4 = projectMatrix
  .in(file("lz4"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(commonSettings("lz4"))
  .settings(
    name := "zio-streams-compress-lz4",
    libraryDependencies ++= Seq(
      "org.lz4" % "lz4-java" % V.lz4
    ),
  )
  .jvmPlatform(scalaVersions)

lazy val snappy = projectMatrix
  .in(file("snappy"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(commonSettings("snappy"))
  .settings(
    name := "zio-streams-compress-snappy",
    libraryDependencies ++= Seq(
      "org.xerial.snappy" % "snappy-java" % V.snappy
    ),
  )
  .jvmPlatform(scalaVersions)

lazy val tar = projectMatrix
  .in(file("tar"))
  .dependsOn(core % "compile->compile;test->test")
  .dependsOn(gzip % "test")
  .settings(commonSettings("tar"))
  .settings(
    libraryDependencies ++= Seq(
      "org.apache.commons" % "commons-compress" % V.commonsCompress
    )
  )
  .jvmPlatform(scalaVersions)

lazy val zip = projectMatrix
  .in(file("zip"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(commonSettings("zip"))
  .jvmPlatform(scalaVersions)

lazy val zip4j = projectMatrix
  .in(file("zip4j"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(commonSettings("zip4j"))
  .settings(
    libraryDependencies ++= Seq(
      "net.lingala.zip4j" % "zip4j" % V.zip4j
    )
  )
  .jvmPlatform(scalaVersions)

lazy val zstd = projectMatrix
  .in(file("zstd"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(commonSettings("zstd"))
  .settings(
    libraryDependencies ++= Seq(
      "com.github.luben" % "zstd-jni" % V.zstdJni
    )
  )
  .jvmPlatform(scalaVersions)

lazy val example = projectMatrix
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
  .jvmPlatform(scalaVersions)

lazy val docs = project
  .in(file("docs-project"))
  .dependsOn(core.jvm(_scala213))
  .enablePlugins(WebsitePlugin)
  .settings(commonSettings("docs"))
  .settings(
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    crossScalaVersions := List(_scala213),
    projectName := "ZIO Streams Compress docs",
    mainModuleName := "zio-streams-compress-docs",
    projectStage := ProjectStage.ProductionReady,
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(core.jvm(_scala213)),
    readmeCredits :=
      "This library is heavily inspired by [fs2-compress](https://github.com/lhns/fs2-compress).",
    readmeLicense += s"\n\nCopyright 2024-${java.time.Year.now()} Erik van Oosten and the zio-streams-compress contributors.",
  )
