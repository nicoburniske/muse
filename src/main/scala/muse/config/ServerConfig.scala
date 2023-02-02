package muse.config

case class ServerConfig(domain: Option[String], frontendUrl: String, port: Int, schemaFile: String, nThreads: Int)
