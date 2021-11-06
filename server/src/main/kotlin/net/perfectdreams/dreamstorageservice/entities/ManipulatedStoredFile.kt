package net.perfectdreams.dreamstorageservice.entities

import net.perfectdreams.dreamstorageservice.tables.ManipulatedStoredFiles
import net.perfectdreams.dreamstorageservice.tables.StoredFiles
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class ManipulatedStoredFile(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<ManipulatedStoredFile>(ManipulatedStoredFiles)
    var mimeType by ManipulatedStoredFiles.mimeType
    var createdAt by ManipulatedStoredFiles.createdAt
    var cropX by ManipulatedStoredFiles.cropX
    var cropY by ManipulatedStoredFiles.cropY
    var cropWidth by ManipulatedStoredFiles.cropWidth
    var cropHeight by ManipulatedStoredFiles.cropHeight
    var size by ManipulatedStoredFiles.size

    var storedFile by StoredFile referencedOn ManipulatedStoredFiles.storedFile
    var data by ManipulatedStoredFiles.data
}