val scala3Version = "3.5.1"
val http4sVersion = "1.0.0-M42"
val circeVersion = "0.14.1"
val kafkaVersion = "2.8.0"

lazy val root = project
  .in(file("."))
  .settings(
    name := "Scala 3 Project with Typelevel, Kafka",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,

  libraryDependencies ++= Seq(
    "org.http4s" %% "http4s-ember-client" % http4sVersion,
    "org.http4s" %% "http4s-ember-server" % http4sVersion,
    "org.http4s" %% "http4s-dsl"          % http4sVersion,
    "org.http4s" %% "http4s-client" % http4sVersion,
    "org.http4s" %% "http4s-circe" % http4sVersion,
    "io.circe" %% "circe-core" % circeVersion,
    "io.circe" %% "circe-generic" % circeVersion,
    "io.circe" %% "circe-parser" % circeVersion,
    "com.github.fd4s" %% "fs2-kafka" % "3.5.1",
    "org.typelevel" %% "log4cats-slf4j"   % "2.7.0",
    "org.typelevel" %% "log4cats-core"    % "2.7.0",
    )
  )


