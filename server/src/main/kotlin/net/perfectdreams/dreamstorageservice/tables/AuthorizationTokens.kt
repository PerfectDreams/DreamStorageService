package net.perfectdreams.dreamstorageservice.tables

import org.jetbrains.exposed.dao.id.LongIdTable

object AuthorizationTokens : LongIdTable() {
    val token = text("token")
    val description = text("description")
    val namespace = text("namespace")
}