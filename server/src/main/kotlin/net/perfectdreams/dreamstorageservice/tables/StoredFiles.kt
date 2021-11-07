package net.perfectdreams.dreamstorageservice.tables

import net.perfectdreams.dreamstorageservice.utils.UploadedAsFileType
import net.perfectdreams.dreamstorageservice.utils.exposed.postgresEnumeration
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.timestamp

object StoredFiles : LongIdTable() {
    val uploadedAsFileType = postgresEnumeration<UploadedAsFileType>("uploaded_as_file_type")
    val mimeType = text("mime_type")
    val originalExtension = text("original_extension")
    val shaHash = binary("sha_hash").index()
    val uploadedAt = timestamp("uploaded_at")
    val createdBy = reference("created_by", AuthorizationTokens)
    val data = binary("data")

    init {
        index(true, id, shaHash)
    }
}