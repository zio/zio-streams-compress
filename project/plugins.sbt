val zioSbtVersion = "0.4.5"

addSbtPlugin("dev.zio" % "zio-sbt-ecosystem" % zioSbtVersion)
addSbtPlugin("dev.zio" % "zio-sbt-website" % zioSbtVersion)
addSbtPlugin("dev.zio" % "zio-sbt-ci" % zioSbtVersion)

addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.14.5")
addSbtPlugin("org.typelevel" % "sbt-tpolecat" % "0.5.2")
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.11.4")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "1.1.4")

resolvers ++= Resolver.sonatypeOssRepos("public")

addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.11.0")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.20.1")
