package muse.domain.tables

final case class AppUser(
    id: String,
    // TODO: remove access token.
    accessToken: String,
    refreshToken: String
)
