package net.perfectdreams.dreamstorageservice.entities

import org.jetbrains.exposed.dao.id.EntityID

data class AuthorizationToken(
    val id: EntityID<Long>,
    val token: String,
    val description: String,
    val namespace: String
)