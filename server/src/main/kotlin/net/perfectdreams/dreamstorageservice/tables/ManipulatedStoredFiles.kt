package net.perfectdreams.dreamstorageservice.tables

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.timestamp

object ManipulatedStoredFiles : LongIdTable() {
    val mimeType = text("mime_type")
    val createdAt = timestamp("created_at")
    val cropX = integer("crop_x").nullable()
    val cropY = integer("crop_x").nullable()
    val cropWidth = integer("crop_width").nullable()
    val cropHeight = integer("crop_height").nullable()
    val scale = integer("scale").nullable()

    val storedFile = reference("stored_file", StoredFiles).index()
    val data = binary("data")

    init {
        index(true, id, cropX, cropY, cropWidth, cropHeight, scale)
    }
}