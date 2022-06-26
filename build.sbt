ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / organization := "io.nicoburniske"

ThisBuild / scalaVersion := "3.1.0"

val zio        = "2.0.0-RC6"
val zioJson    = "0.3.0-RC8"
val zioConfig  = "3.0.0-RC9"
val zhttp      = "2.0.0-RC9"
val protoQuill = "4.0.0-RC1"
val postgresql = "42.3.4"

lazy val mainMethod = "muse.Main"

inThisBuild(
  List(
    developers    := List(
      Developer(
        "nicoburniske",
        "Nico Burniske",
        "nicoburniske@gmail.com",
        url("https://github.com/nicoburniske")
      )),
    onLoadMessage := ConsoleHelper.welcomeMessage
  )
)

lazy val root = (project in file(".")).settings(
  name                      := "muse",
  Compile / run / mainClass := Some(mainMethod),
  reStart / mainClass       := Some(mainMethod),
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
    // Logging.
    "dev.zio"                       %% "zio-logging"                   % "2.0.0-RC10",
    "ch.qos.logback"                 % "logback-classic"               % "1.2.11",
    // Graphql.
    "com.github.ghostdogpr"         %% "caliban"                       % "2.0.0-RC2+65-0d8061df-SNAPSHOT",
    "com.github.ghostdogpr"         %% "caliban-zio-http"              % "2.0.0-RC2+65-0d8061df-SNAPSHOT",
    // Test Libraries.
    "dev.zio"                       %% "zio-test"                      % "2.0.0-RC6" % Test
  ),
  scalacOptions ++= Seq(
    "-Xmax-inlines:45"
  ),
  testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
  resolvers ++= Seq(
    "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
  ),
  excludeDependencies ++= Seq(
    ExclusionRule("org.scala-lang.modules", "scala-collection-compat_2.13")
  )
)
