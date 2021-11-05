package net.perfectdreams.dreamstorageservice.tables

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.timestamp

object StoredFiles : LongIdTable() {
    val mimeType = text("mime_type")
    val shaHash = binary("sha_hash").index()
    val uploadedAt = timestamp("uploaded_at")
    val createdBy = reference("created_by", AuthorizationTokens)
    val data = binary("data")

    init {
        index(true, id, shaHash)
    }
}