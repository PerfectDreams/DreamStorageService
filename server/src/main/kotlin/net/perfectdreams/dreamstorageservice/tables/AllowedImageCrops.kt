package net.perfectdreams.dreamstorageservice.tables

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.timestamp

object AllowedImageCrops : LongIdTable() {
    val createdAt = timestamp("created_at")
    val cropX = integer("crop_x")
    val cropY = integer("crop_y")
    val cropWidth = integer("crop_width")
    val cropHeight = integer("crop_height")

    val storedFile = reference("stored_file", StoredFiles).index()

    init {
        index(true, storedFile, cropX, cropY, cropWidth, cropHeight)
    }
}