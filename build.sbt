//import sbt.Def
//import MimaSettings.mimaSettings

val V = new {
  // val betterMonadicFor = "0.3.1"
  val brotli = "0.1.2"
  val commonsCompress = "1.27.1"
  val logbackClassic = "1.5.8"
  val lz4 = "1.8.0"
  val zio = "2.1.11"
  val zip4j = "2.11.5"
  val zstdJni = "1.5.6-6"
}

enablePlugins(ZioSbtEcosystemPlugin, ZioSbtCiPlugin)

lazy val _scala212 = "2.12.20"
lazy val _scala213 = "2.13.15"
lazy val _scala3 = "3.3.4"
lazy val scalaVersions = Seq(_scala3, _scala213, _scala212)

inThisBuild(
  List(
    name := "ZIO Streams Compress",
    scalaVersion := _scala213,
    // zio-sbt defines these 'scala*' settings, but we need to define them here to override the defaults and better control them
    scala212 := _scala212,
    scala213 := _scala213,
    scala3 := _scala3,
    crossScalaVersions := List(scala3.value, scala213.value, scala212.value),
    ciEnabledBranches := Seq("master"),
    run / fork := true,
    ciJvmOptions ++= Seq("-Xms6G", "-Xmx4G", "-Xss4M", "-XX:+UseG1GC"),
    scalafixDependencies ++= List(
      "com.github.vovapolu" %% "scaluzzi" % "0.1.23",
      "io.github.ghostbuster91.scalafix-unified" %% "unified" % "0.0.9",
    ),
    developers := List(
      Developer(
        "erikvanoosten",
        "Erik van Oosten",
        "",
        url("https://github.com/erikvanoosten"),
      )
    ),
  )
)

def commonSettings(projectName: String) = Seq(
  name := s"zio-streams-compress-$projectName",
//    Compile / compile / scalacOptions ++=
//      optionsOn("2.13")("-Wconf:cat=unused-nowarn:s").value,
  // scalacOptions -= "-Xlint:infer-any",
  // workaround for bad constant pool issue
  //  (Compile / doc) := Def.taskDyn {
  //    val default = (Compile / doc).taskValue
  //    Def.task(default.value)
  //  }.value,
  //  Test / scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule)),
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
  semanticdbEnabled := true,
  semanticdbVersion := scalafixSemanticdb.revision,
) ++ scalafixSettings
// .aggregate(docs)

lazy val root =
  project
    .in(file("."))
//    .settings(commonSettings)
    .settings(
      name := "zio-streams-compress",
      publish / skip := true,
      crossScalaVersions := Nil, // https://www.scala-sbt.org/1.x/docs/Cross-Build.html#Cross+building+a+project+statefully,
      publishArtifact := false,
      // commands += lint
    )
    .aggregate(core.projectRefs: _*)
    .aggregate(gzip.projectRefs: _*)
    .aggregate(zip.projectRefs: _*)
    .aggregate(zip4j.projectRefs: _*)
    .aggregate(tar.projectRefs: _*)
    .aggregate(zstd.projectRefs: _*)
    .aggregate(bzip2.projectRefs: _*)
    .aggregate(brotli.projectRefs: _*)
    .aggregate(lz4.projectRefs: _*)
    .aggregate(example.projectRefs: _*)

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

lazy val gzip = projectMatrix
  .in(file("gzip"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(commonSettings("gzip"))
  .jvmPlatform(scalaVersions)
//.jsPlatform(scalaVersions)

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

lazy val example = projectMatrix
  .in(file("example"))
  .dependsOn(gzip, tar, zip)
  .settings(commonSettings("example"))
  .settings(
    publishArtifact := false,
    publish / skip := true,
  )
  .settings(
    name := "zio-streams-compress-example"
  )
  .jvmPlatform(scalaVersions)
