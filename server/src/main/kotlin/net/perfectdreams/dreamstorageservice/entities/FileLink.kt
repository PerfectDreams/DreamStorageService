package net.perfectdreams.dreamstorageservice.entities

import net.perfectdreams.dreamstorageservice.tables.FileLinks
import org.jetbrains.exposed.dao.id.EntityID

class FileLink(id: EntityID<String>) : TextEntity(id) {
    companion object : TextEntityClass<FileLink>(FileLinks)
    var path by FileLinks.id
    var createdAt by FileLinks.createdAt
    var storedFile by StoredFile referencedOn FileLinks.storedFile
    val storedFileId by FileLinks.storedFile
}