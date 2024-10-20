val zioSbtVersion = "0.4.0-alpha.28"

addSbtPlugin("dev.zio" % "zio-sbt-ecosystem" % zioSbtVersion)
addSbtPlugin("dev.zio" % "zio-sbt-website" % zioSbtVersion)
addSbtPlugin("dev.zio" % "zio-sbt-ci" % zioSbtVersion)

addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.13.0")
addSbtPlugin("org.typelevel" % "sbt-tpolecat" % "0.5.2")
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.10.4")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "1.1.4")

resolvers ++= Resolver.sonatypeOssRepos("public")

addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.10.0")
//addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.2.1")
//addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.6.4")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.17.0")
//addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.2")
//addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.11.3")
