package net.perfectdreams.dreamstorageservice.tables

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.timestamp

object ImageLinks : LongIdTable() {
    val folder = text("folder").index()
    val file = text("file").index()
    val createdAt = timestamp("created_at")
    val createdBy = reference("created_by", AuthorizationTokens)
    val storedImage = reference("stored_image", StoredImages).index()
}