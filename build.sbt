ThisBuild / version      := "0.1.0"
ThisBuild / organization := "io.nicoburniske"
ThisBuild / name         := "muse"
ThisBuild / scalaVersion := "3.2.2"

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
      "dev.zio"                       %% "zio"                           % Version.zio,
      "dev.zio"                       %% "zio-prelude"                   % Version.zioPrelude,
      "dev.zio"                       %% "zio-schema"                    % Version.zioSchema,
      "dev.zio"                       %% "zio-schema-json"               % Version.zioSchema,
      "dev.zio"                       %% "zio-json"                      % Version.zioJson,
      "dev.zio"                       %% "zio-nio"                       % Version.zioNio,
      "dev.zio"                       %% "zio-cache"                     % Version.zioCache,
      "dev.zio"                       %% "zio-redis"                     % Version.zioRedis,
      "dev.zio"                       %% "zio-metrics-prometheus"        % Version.zioMetrics,
      "dev.zio"                       %% "zio-metrics-connectors"        % Version.zioMetrics,
      "com.stuart"                    %% "zcaffeine"                     % Version.zcaffiene,
      "nl.vroste"                     %% "rezilience"                    % Version.rezilience,
      // ZIO Config.
      "dev.zio"                       %% "zio-config"                    % Version.zioConfig,
      "dev.zio"                       %% "zio-config-typesafe"           % Version.zioConfig,
      // HTTP Server.
      "dev.zio"                       %% "zio-http"                      % Version.zioHttp,
      // HTTP Client.
      "com.softwaremill.sttp.client3" %% "core"                          % Version.sttp,
      "com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % Version.sttp,
      "com.softwaremill.sttp.client3" %% "zio-json"                      % Version.sttp,
      // Database.
      "io.getquill"                   %% "quill-jdbc-zio"                % Version.protoQuill,
      "org.postgresql"                 % "postgresql"                    % Version.postgresql,
      "org.flywaydb"                   % "flyway-core"                   % Version.flyway,
      // Logging.
      "dev.zio"                       %% "zio-logging-slf4j"             % Version.zioLogging,
      "org.slf4j"                      % "slf4j-api"                     % Version.slf4j,
      "ch.qos.logback"                 % "logback-classic"               % Version.logback,
      // Graphql.
      "com.github.ghostdogpr"         %% "caliban"                       % Version.caliban,
      "com.github.ghostdogpr"         %% "caliban-zio-http"              % Version.caliban,
      "com.softwaremill.sttp.tapir"   %% "tapir-json-zio"                % "1.2.11",
      // Test Libraries.
      "dev.zio"                       %% "zio-test"                      % Version.zio % Test
    ),
    scalacOptions ++= Seq(
      "-Xmax-inlines:55"
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    resolvers ++= Seq(
      "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
    ),
    excludeDependencies ++= Seq(
      ExclusionRule("org.scala-lang.modules", "scala-collection-compat_2.13"),
      ExclusionRule("com.lihaoyi", "geny_2.13")
    ),
    assembly / assemblyMergeStrategy := {
      case PathList("module-info.class") => MergeStrategy.discard
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case _                             => MergeStrategy.first
    }
  ).settings(dockerSettings*)

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
