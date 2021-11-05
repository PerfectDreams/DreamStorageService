package net.perfectdreams.dreamstorageservice.entities

data class AuthorizationToken(
    val token: String,
    val description: String,
    val allowedFilePath: String
)