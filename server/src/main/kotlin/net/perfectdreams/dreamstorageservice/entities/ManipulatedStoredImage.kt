package net.perfectdreams.dreamstorageservice.entities

import net.perfectdreams.dreamstorageservice.tables.ManipulatedStoredImages
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class ManipulatedStoredImage(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<ManipulatedStoredImage>(ManipulatedStoredImages)
    var mimeType by ManipulatedStoredImages.mimeType
    var createdAt by ManipulatedStoredImages.createdAt
    var cropX by ManipulatedStoredImages.cropX
    var cropY by ManipulatedStoredImages.cropY
    var cropWidth by ManipulatedStoredImages.cropWidth
    var cropHeight by ManipulatedStoredImages.cropHeight
    var size by ManipulatedStoredImages.size

    var storedImage by StoredImage referencedOn ManipulatedStoredImages.storedImage
    var data by ManipulatedStoredImages.data
}