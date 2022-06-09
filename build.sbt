ThisBuild / scalaVersion     := "3.1.0"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "io.nicoburniske"
ThisBuild / organizationName := "example"

lazy val root = (project in file(".")).settings(
  name := "spotify",
  libraryDependencies ++= Seq(
    "dev.zio"                       %% "zio"                           % "2.0.0-RC6",
    "dev.zio"                       %% "zio-json"                      % "0.3.0-RC8",
    // ZIO Config.
    "dev.zio"                       %% "zio-config"                    % "3.0.0-RC9",
    "dev.zio"                       %% "zio-config-magnolia"           % "3.0.0-RC9",
    "dev.zio"                       %% "zio-config-typesafe"           % "3.0.0-RC9",
    "dev.zio"                       %% "zio-config-yaml"               % "3.0.0-RC9",
    // HTTP Server.
    "io.d11"                        %% "zhttp"                         % "2.0.0-RC9",
    // HTTP Client.
    "com.softwaremill.sttp.client3" %% "core"                          % "3.6.2",
    "com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % "3.6.2",

    // Postgres Async.
    "io.getquill" %% "quill-jasync-postgres" % "3.12.0.Beta1.7",

    // Test Libraries.
    "dev.zio" %% "zio-test" % "2.0.0-RC6" % Test
  ),
  testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
  excludeDependencies ++= Seq(
    ExclusionRule("org.scala-lang.modules", "scala-collection-compat_2.13"),
    ExclusionRule("com.lihaoyi", "sourcecode_2.13"),
    ExclusionRule("com.lihaoyi", "fansi_2.13"),
    ExclusionRule("com.lihaoyi", "pprint_2.13")
  )
)
