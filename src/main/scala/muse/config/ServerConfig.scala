package muse.config

case class ServerConfig(frontendUrl: String, port: Int, schemaFile: String, userSessionsFile: String, nThreads: Int)
