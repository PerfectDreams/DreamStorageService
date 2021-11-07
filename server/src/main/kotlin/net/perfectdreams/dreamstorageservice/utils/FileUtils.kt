package net.perfectdreams.dreamstorageservice.utils

import io.ktor.http.*
import net.perfectdreams.dreamstorageservice.DreamStorageService
import net.perfectdreams.dreamstorageservice.entities.AuthorizationToken
import net.perfectdreams.dreamstorageservice.entities.FileLink
import net.perfectdreams.dreamstorageservice.entities.StoredFile
import net.perfectdreams.dreamstorageservice.tables.FileLinks
import net.perfectdreams.dreamstorageservice.tables.StoredFiles
import org.jetbrains.exposed.dao.DaoEntityID
import java.security.MessageDigest
import java.time.Instant

class FileUtils(val m: DreamStorageService) {
    suspend fun uploadFileAndCreateFileLink(
        token: AuthorizationToken,
        path: String,
        originalExtension: String,
        checksum: ByteArray,
        uploadedAsFileType: UploadedAsFileType,
        contentType: ContentType,
        fileData: ByteArray
    ): Pair<StoredFile, FileLink> {
        return m.transaction {
            var storedFile = StoredFile.find { StoredFiles.shaHash eq checksum }
                .firstOrNull()

            if (storedFile == null) {
                // Create stored file
                storedFile = StoredFile.new {
                    this.mimeType = contentType.toString()
                    this.originalExtension = originalExtension
                    this.shaHash = checksum
                    this.uploadedAt = Instant.now()
                    this.createdBy = token.id
                    this.data = fileData
                    this.uploadedAsFileType = uploadedAsFileType
                }
            }

            // Replace the current file link, if it exists
            val existingFileLink = FileLink.findById(path)
            // We don't call it directly (storedFile) to avoid loading the blob just to check if the file is still used or not
            val previousStoredFileId = existingFileLink?.storedFileId
            existingFileLink?.delete()

            // Create link
            val newFileLink = FileLink.new {
                // I don't know why it needs to be like this, this is a horrible hack
                // If we set the ID in the ".new(idhere) {}" call, it fails with a weird "created_at" is missing error
                // See: https://github.com/JetBrains/Exposed/issues/1379
                this.path = DaoEntityID(path, FileLinks)
                this.createdAt = Instant.now()
                this.createdBy = token.id
                this.storedFile = storedFile
            }

            if (previousStoredFileId != null)
                m.checkAndCleanUpFile(previousStoredFileId.value)

            return@transaction Pair(storedFile, newFileLink)
        }
    }

    // MessageDigest is not thread safe!
    fun calculateChecksum(array: ByteArray) = MessageDigest.getInstance("SHA-256").digest(array)
}