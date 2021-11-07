package net.perfectdreams.dreamstorageservice.entities

import net.perfectdreams.dreamstorageservice.tables.ImageLinks
import net.perfectdreams.dreamstorageservice.tables.StoredImages
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class ImageLink(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<ImageLink>(ImageLinks)
    var folder by ImageLinks.folder
    var file by ImageLinks.file
    var createdAt by ImageLinks.createdAt
    var createdBy by ImageLinks.createdBy
    var storedImage by StoredImage referencedOn ImageLinks.storedImage
    var storedImageId by ImageLinks.storedImage
}