package muse.domain.tables

final case class AppUser(
    id: String,
    accessToken: String,
    refreshToken: String
)
