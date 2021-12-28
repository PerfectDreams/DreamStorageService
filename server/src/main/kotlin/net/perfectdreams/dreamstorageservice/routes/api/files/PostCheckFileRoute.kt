package net.perfectdreams.dreamstorageservice.routes.api.files

import io.ktor.application.*
import io.ktor.http.content.*
import io.ktor.request.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import net.perfectdreams.dreamstorageservice.DreamStorageService
import net.perfectdreams.dreamstorageservice.data.api.CheckFileResponse
import net.perfectdreams.dreamstorageservice.data.api.FileDoesNotExistResponse
import net.perfectdreams.dreamstorageservice.data.api.FileExistsResponse
import net.perfectdreams.dreamstorageservice.entities.AuthorizationToken
import net.perfectdreams.dreamstorageservice.routes.api.RequiresAPIAuthenticationRoute
import net.perfectdreams.dreamstorageservice.tables.StoredFiles
import net.perfectdreams.dreamstorageservice.tables.StoredImages
import net.perfectdreams.dreamstorageservice.utils.ktor.respondJson
import org.apache.commons.codec.binary.Hex
import org.jetbrains.exposed.sql.select

class PostCheckFileRoute(m: DreamStorageService) : RequiresAPIAuthenticationRoute(m, "/files/check") {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override suspend fun onAuthenticatedRequest(call: ApplicationCall, token: AuthorizationToken) {
        withContext(Dispatchers.IO) {
            // Receive the uploaded file
            val multipart = call.receiveMultipart()
            val parts = multipart.readAllParts()
            val filePart = parts.first { it.name == "file" } as PartData.FileItem

            val fileToBeStored = filePart.streamProvider.invoke().readAllBytes()
            val contentType = filePart.contentType ?: error("Missing Content-Type!")

            // Calculate the file's checksum
            val checksum = m.fileUtils.calculateChecksum(fileToBeStored)

            // First check if the image exists based on the original checksum of the file
            // This is useful because we don't need to optimize the image just to find out that image already exists
            val alreadyStoredFile = m.transaction {
                StoredFiles.slice(StoredFiles.id, StoredFiles.shaHash).select {
                    StoredFiles.shaHash eq checksum
                }.firstOrNull()
            }

            if (alreadyStoredFile != null) {
                call.respondJson<CheckFileResponse>(
                    FileExistsResponse(
                        alreadyStoredFile[StoredImages.id].value,
                        Hex.encodeHexString(alreadyStoredFile[StoredFiles.shaHash])
                    )
                )
                return@withContext
            }

            call.respondJson<CheckFileResponse>(
                FileDoesNotExistResponse()
            )
        }
    }
}