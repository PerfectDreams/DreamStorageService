package net.perfectdreams.dreamstorageservice.entities

import net.perfectdreams.dreamstorageservice.tables.StoredFiles
import net.perfectdreams.dreamstorageservice.tables.StoredImages
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class StoredImage(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<StoredImage>(StoredImages)
    var mimeType by StoredImages.mimeType
    var shaHash by StoredImages.shaHash
    var originalShaHash by StoredImages.originalShaHash
    var uploadedAt by StoredImages.uploadedAt
    var createdBy by StoredImages.createdBy
    var data by StoredImages.data
}