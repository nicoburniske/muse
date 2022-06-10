ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / organization := "io.nicoburniske"
// ThisBuild / names        := "spotify"

ThisBuild / scalaVersion := "3.1.0"

val zio        = "2.0.0-RC6"
val zioJson    = "0.3.0-RC8"
val zioConfig  = "3.0.0-RC9"
val zhttp      = "2.0.0-RC9"
val protoQuill = "4.0.0-RC1"
val postgresql = "42.3.4"

lazy val root = (project in file(".")).settings(
  name := "spotify",
  libraryDependencies ++= Seq(
    "dev.zio"                       %% "zio"                           % zio,
    "dev.zio"                       %% "zio-json"                      % zioJson,
    // ZIO Config.
    "dev.zio"                       %% "zio-config"                    % zioConfig,
    "dev.zio"                       %% "zio-config-typesafe"           % zioConfig,
    "dev.zio"                       %% "zio-config-yaml"               % zioConfig,
    // HTTP Server.
    "io.d11"                        %% "zhttp"                         % zhttp,
    // HTTP Client.
    "com.softwaremill.sttp.client3" %% "core"                          % "3.6.2",
    "com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % "3.6.2",
    // Quill JDBC ZIO.
    "io.getquill"                   %% "quill-jdbc-zio"                % protoQuill,
    "org.postgresql"                 % "postgresql"                    % postgresql,
    // Test Libraries.
    "dev.zio"                       %% "zio-test"                      % "2.0.0-RC6" % Test
  ),
  testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
  excludeDependencies ++= Seq(
    ExclusionRule("org.scala-lang.modules", "scala-collection-compat_2.13")
  )
)
