package net.perfectdreams.dreamstorageservice.entities

import net.perfectdreams.dreamstorageservice.tables.StoredFiles
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class StoredFile(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<StoredFile>(StoredFiles)
    var mimeType by StoredFiles.mimeType
    var shaHash by StoredFiles.shaHash
    var uploadedAt by StoredFiles.uploadedAt
    var createdBy by StoredFiles.createdBy
    var data by StoredFiles.data
}