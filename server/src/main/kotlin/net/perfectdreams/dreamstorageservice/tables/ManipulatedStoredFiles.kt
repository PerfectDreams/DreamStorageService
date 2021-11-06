package net.perfectdreams.dreamstorageservice.tables

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.timestamp

object ManipulatedStoredFiles : LongIdTable() {
    val mimeType = text("mime_type")
    val createdAt = timestamp("created_at")
    val cropX = integer("crop_x").index().nullable()
    val cropY = integer("crop_y").index().nullable()
    val cropWidth = integer("crop_width").index().nullable()
    val cropHeight = integer("crop_height").index().nullable()
    val size = integer("size").index().nullable()

    val storedFile = reference("stored_file", StoredFiles).index()
    val data = binary("data")

    init {
        index(true, storedFile, cropX, cropY, cropWidth, cropHeight, size)
    }
}