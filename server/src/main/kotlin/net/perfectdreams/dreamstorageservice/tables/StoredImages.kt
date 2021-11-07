package net.perfectdreams.dreamstorageservice.tables

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.timestamp

object StoredImages : LongIdTable() {
    val mimeType = text("mime_type")
    val shaHash = binary("sha_hash").index()
    val originalShaHash = binary("original_sha_hash").index() // To avoid optimizing something just to find that the file already exists
    val uploadedAt = timestamp("uploaded_at")
    val createdBy = reference("created_by", AuthorizationTokens)
    val data = binary("data")

    init {
        index(true, originalShaHash)
    }
}