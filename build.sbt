ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / organization := "io.nicoburniske"

ThisBuild / scalaVersion := "3.1.0"

val zio          = "2.0.0"
val zioJson      = "0.3.0-RC10"
val zioConfig    = "3.0.1"
val zhttp        = "2.0.0-RC10"
val protoQuill   = "4.0.0"
val postgresql   = "42.3.6"
val sttp         = "3.7.0"
val slf4jVersion = "2.0.1"

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
  name                             := "muse",
  Compile / run / mainClass        := Some(mainMethod),
  reStart / mainClass              := Some(mainMethod),
  assembly / mainClass             := Some(mainMethod),
  assembly / assemblyJarName       := "muse.jar",
  libraryDependencies ++= Seq(
    "dev.zio"                       %% "zio"                           % zio,
    "dev.zio"                       %% "zio-json"                      % zioJson,
    "dev.zio"                       %% "zio-nio"                       % zio,
    // ZIO Config.
    "dev.zio"                       %% "zio-config"                    % zioConfig,
    "dev.zio"                       %% "zio-config-typesafe"           % zioConfig,
    // HTTP Server.
    "io.d11"                        %% "zhttp"                         % zhttp,
    // HTTP Client.
    "com.softwaremill.sttp.client3" %% "core"                          % sttp,
    "com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % sttp,
    // Quill JDBC ZIO.
    "io.getquill"                   %% "quill-jdbc-zio"                % protoQuill,
    "org.postgresql"                 % "postgresql"                    % postgresql,
    // Logging.
    "dev.zio"                       %% "zio-logging-slf4j"             % zio,
    "org.slf4j"                      % "slf4j-api"                     % slf4jVersion,
    "ch.qos.logback"                 % "logback-classic"               % "1.4.1",
//    "org.slf4j"                      % "slf4j-simple"                  % slf4jVersion,
    // Graphql.
    "com.github.ghostdogpr"         %% "caliban"                       % "2.0.1",
    "com.github.ghostdogpr"         %% "caliban-zio-http"              % "2.0.1",
    // Test Libraries.
    "dev.zio"                       %% "zio-test"                      % zio % Test
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
  ),
  assembly / assemblyMergeStrategy := {
    case PathList("module-info.class") => MergeStrategy.discard
    case PathList("META-INF", xs @ _*) => MergeStrategy.discard
    case _                             => MergeStrategy.first
  }
)
