package net.perfectdreams.dreamstorageservice.entities

import net.perfectdreams.dreamstorageservice.tables.FileLinks
import net.perfectdreams.dreamstorageservice.tables.StoredFiles
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class FileLink(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<FileLink>(FileLinks)
    var path by FileLinks.id
    var folder by FileLinks.folder
    var file by FileLinks.file
    var createdAt by FileLinks.createdAt
    var createdBy by FileLinks.createdBy
    var storedFile by StoredFile referencedOn FileLinks.storedFile
    var storedFileId by FileLinks.storedFile
}