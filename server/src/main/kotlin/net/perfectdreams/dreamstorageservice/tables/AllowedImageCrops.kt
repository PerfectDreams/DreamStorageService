package net.perfectdreams.dreamstorageservice.tables

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.timestamp

object AllowedImageCrops : LongIdTable() {
    val createdAt = timestamp("created_at")
    val cropX = integer("crop_x")
    val cropY = integer("crop_y")
    val cropWidth = integer("crop_width")
    val cropHeight = integer("crop_height")

    val storedImage = reference("stored_image", StoredImages).index()

    init {
        index(true, storedImage, cropX, cropY, cropWidth, cropHeight)
    }
}