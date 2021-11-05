package net.perfectdreams.dreamstorageservice.routes

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import net.perfectdreams.dreamstorageservice.DreamStorageService
import net.perfectdreams.dreamstorageservice.data.UploadFileRequest
import net.perfectdreams.dreamstorageservice.data.UploadFileResponse
import net.perfectdreams.dreamstorageservice.entities.AuthorizationToken
import net.perfectdreams.dreamstorageservice.entities.FileLink
import net.perfectdreams.dreamstorageservice.entities.StoredFile
import net.perfectdreams.dreamstorageservice.tables.FileLinks
import net.perfectdreams.dreamstorageservice.tables.StoredFiles
import org.apache.commons.codec.binary.Hex
import org.jetbrains.exposed.dao.DaoEntityID
import java.security.MessageDigest
import java.time.Instant

class PutUploadFileRoute(m: DreamStorageService) : RequiresAPIAuthenticationRoute(m, "/api/v1/upload") {
    companion object {
        private val SHA_256 = MessageDigest.getInstance("SHA-256")
        private val logger = KotlinLogging.logger {}
    }

    override suspend fun onAuthenticatedRequest(call: ApplicationCall, token: AuthorizationToken) {
        withContext(Dispatchers.IO) {
            // Receive the uploaded file
            val multipart = call.receiveMultipart()
            val parts = multipart.readAllParts()
            val filePart = parts.first { it.name == "file" } as PartData.FileItem
            val attributesPart = parts.first { it.name == "attributes" } as PartData.FormItem

            val attributes = Json.decodeFromString<UploadFileRequest>(attributesPart.value)
            val unformattedPath = attributes.path

            val fileToBeStored = filePart.streamProvider.invoke().readAllBytes()
            val contentType = filePart.contentType

            // Calculate the checksum
            val checksum = calculateChecksum(SHA_256, fileToBeStored)

            // Allows the user to format the upload path with a SHA-256 hash, neat!
            val path = unformattedPath.format(Hex.encodeHexString(checksum))
            val fullPath = token.namespace + "/" + path

            // Check if a file with the same hash exists
            val fileLink = m.transaction {
                var storedFile = StoredFile.find { StoredFiles.shaHash eq checksum }
                    .firstOrNull()

                if (storedFile == null) {
                    // Create stored file
                    storedFile = StoredFile.new {
                        this.mimeType = contentType.toString()
                        this.shaHash = checksum
                        this.uploadedAt = Instant.now()
                        this.createdBy = token.id
                        this.data = fileToBeStored
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

                return@transaction newFileLink
            }

            logger.info { "Uploaded file ${fileLink.path}" }

            call.respondText(
                Json.encodeToString(
                    UploadFileResponse(
                        fileLink.path.value,
                        fullPath
                    )
                )
            )
        }
    }

    private fun calculateChecksum(digest: MessageDigest, array: ByteArray) = digest.digest(array)
}