package muse.config

final case class SpotifyConfig(clientID: String, clientSecret: String, redirectURI: String, service: SpotifyServiceConfig)
final case class SpotifyServiceConfig(
    artistCacheSize: Int,
    albumCacheSize: Int,
    userCacheSize: Int,
    playlistCacheSize: Int,
    likedSongsCacheSize: Int
)
