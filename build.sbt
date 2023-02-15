ThisBuild / version      := "0.1.0"
ThisBuild / organization := "io.nicoburniske"
ThisBuild / name         := "muse"
ThisBuild / scalaVersion := "3.1.0"

val zio          = "2.0.0"
val zioJson      = "0.4.2"
val zioConfig    = "3.0.1"
val zhttp        = "2.0.0-RC10"
val protoQuill   = "4.6.0"
val postgresql   = "42.3.6"
val flyway       = "8.5.12"
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

lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .settings(
    name                             := "muse",
    assembly / assemblyJarName       := "muse.jar",
    Compile / mainClass              := Some(mainMethod),
    reStart / mainClass              := Some(mainMethod),
    assembly / mainClass             := Some(mainMethod),
    Compile / discoveredMainClasses  := Seq(),
    libraryDependencies ++= Seq(
      "dev.zio"                       %% "zio"                           % zio,
      "dev.zio"                       %% "zio-json"                      % zioJson,
      "dev.zio"                       %% "zio-nio"                       % zio,
      "dev.zio"                       %% "zio-cache"                     % "0.2.0",
      "dev.zio"                       %% "zio-metrics-prometheus"        % zio,
      "dev.zio"                       %% "zio-metrics-connectors"        % zio,
      "com.stuart"                    %% "zcaffeine"                     % "1.0.0-M2",
      // ZIO Config.
      "dev.zio"                       %% "zio-config"                    % zioConfig,
      "dev.zio"                       %% "zio-config-typesafe"           % zioConfig,
      // HTTP Server.
      "io.d11"                        %% "zhttp"                         % zhttp,
      // HTTP Client.
      "com.softwaremill.sttp.client3" %% "core"                          % sttp,
      "com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % sttp,
      "com.softwaremill.sttp.client3" %% "zio-json"                      % sttp,
      // Database.
      "io.getquill"                   %% "quill-jdbc-zio"                % protoQuill,
      "org.postgresql"                 % "postgresql"                    % postgresql,
      "org.flywaydb"                   % "flyway-core"                   % flyway,
      // Logging.
      "dev.zio"                       %% "zio-logging-slf4j"             % zio,
      "org.slf4j"                      % "slf4j-api"                     % slf4jVersion,
      "ch.qos.logback"                 % "logback-classic"               % "1.4.1",

      // Graphql.
      "com.github.ghostdogpr" %% "caliban"          % "2.0.1",
      "com.github.ghostdogpr" %% "caliban-zio-http" % "2.0.1",
      // Test Libraries.
      "dev.zio"               %% "zio-test"         % zio % Test
    ),
    scalacOptions ++= Seq(
      "-Xmax-inlines:55"
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
  ).settings(dockerSettings: _*)

lazy val dockerSettings = Seq(
  Docker / packageName := "muse_server",
  Docker / maintainer  := "Nico Burniske",
  dockerUpdateLatest   := false,
  // TODO: Can this be read from config?
  dockerExposedPorts   := Seq(8883, 9091),
  dockerBaseImage      := "azul/zulu-openjdk:17",
  Universal / javaOptions ++= Seq(
    "-J-XX:ActiveProcessorCount=4", // Overrides the automatic detection mechanism of the JVM that doesn't work very well in k8s.
    "-J-XX:MaxRAMPercentage=80.0",  // 80% * 1280Mi = 1024Mi (See https://github.com/conduktor/conduktor-devtools-builds/pull/96/files#diff-1c0a26888454bc51fc9423622b5d4ee82456b0420f169518a371f3f0e23d443cR67-R70)
    "-J-XX:+ExitOnOutOfMemoryError",
    "-J-XX:+HeapDumpOnOutOfMemoryError",
    "-J-XshowSettings:system",      // https://developers.redhat.com/articles/2022/04/19/java-17-whats-new-openjdks-container-awareness#recent_changes_in_openjdk_s_container_awareness_code
    "-Dfile.encoding=UTF-8"
  )
)
