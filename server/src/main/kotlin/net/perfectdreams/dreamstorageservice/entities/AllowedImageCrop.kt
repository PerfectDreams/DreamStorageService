package net.perfectdreams.dreamstorageservice.entities

import net.perfectdreams.dreamstorageservice.tables.AllowedImageCrops
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class AllowedImageCrop(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<AllowedImageCrop>(AllowedImageCrops)
    var createdAt by AllowedImageCrops.createdAt
    var cropX by AllowedImageCrops.cropX
    var cropY by AllowedImageCrops.cropY
    var cropWidth by AllowedImageCrops.cropWidth
    var cropHeight by AllowedImageCrops.cropHeight

    var storedFile by StoredFile referencedOn AllowedImageCrops.storedImage
    var storedImageId by AllowedImageCrops.storedImage
}