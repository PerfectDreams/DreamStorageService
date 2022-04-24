package net.perfectdreams.dreamstorageservice.routes.api.images

import io.ktor.server.application.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import net.perfectdreams.dreamstorageservice.DreamStorageService
import net.perfectdreams.dreamstorageservice.routes.api.RequiresAPIv2AuthenticationRoute
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.perfectdreams.dreamstorageservice.data.api.CheckImageRequest
import net.perfectdreams.dreamstorageservice.data.api.CheckImageResponse
import net.perfectdreams.dreamstorageservice.data.api.ImageDoesNotExistResponse
import net.perfectdreams.dreamstorageservice.data.api.ImageExistsResponse
import net.perfectdreams.dreamstorageservice.entities.AuthorizationToken
import net.perfectdreams.dreamstorageservice.tables.StoredImages
import net.perfectdreams.dreamstorageservice.utils.ktor.respondJson
import org.apache.commons.codec.binary.Hex
import org.jetbrains.exposed.sql.select

class PostCheckImageRoute(m: DreamStorageService) : RequiresAPIv2AuthenticationRoute(m, "/images/check") {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override suspend fun onAuthenticatedRequest(call: ApplicationCall, token: AuthorizationToken) {
        withContext(Dispatchers.IO) {
            // Receive the uploaded file
            val multipart = call.receiveMultipart()
            val parts = multipart.readAllParts()
            val filePart = parts.first { it.name == "file" } as PartData.FileItem
            val attributesPart = parts.first { it.name == "attributes" } as PartData.FormItem

            val attributes = Json.decodeFromString<CheckImageRequest>(attributesPart.value)

            val fileToBeStored = filePart.streamProvider.invoke().readAllBytes()
            val contentType = filePart.contentType ?: error("Missing Content-Type!")

            if (contentType.contentType != "image")
                error("Trying to upload a non-image file via the check image endpoint!")

            var trueContentsToBeStored = fileToBeStored

            // Calculate the file's checksum before optimization
            val originalChecksum = m.fileUtils.calculateChecksum(trueContentsToBeStored)

            // First check if the image exists based on the original checksum of the file
            // This is useful because we don't need to optimize the image just to find out that image already exists
            val alreadyStoredImage = m.transaction {
                StoredImages.slice(StoredImages.id, StoredImages.shaHash, StoredImages.originalShaHash).select {
                    StoredImages.originalShaHash eq originalChecksum
                }.firstOrNull()
            }

            if (alreadyStoredImage != null) {
                call.respondJson<CheckImageResponse>(
                    ImageExistsResponse(
                        alreadyStoredImage[StoredImages.id].value,
                        Hex.encodeHexString(alreadyStoredImage[StoredImages.shaHash]),
                        Hex.encodeHexString(alreadyStoredImage[StoredImages.originalShaHash]),
                    )
                )
                return@withContext
            }

            var checksum = originalChecksum
            if (!attributes.skipOptimizations) {
                // Optimize image...
                trueContentsToBeStored = m.optimizeImage(contentType, trueContentsToBeStored)

                // Calculate the new checksum...
                checksum = m.fileUtils.calculateChecksum(trueContentsToBeStored)

                // Now check if the image exists based on the optimized version of the image
                val alreadyStoredOptimizedImage = m.transaction {
                    StoredImages.slice(StoredImages.id, StoredImages.shaHash, StoredImages.originalShaHash).select {
                        StoredImages.shaHash eq checksum
                    }.firstOrNull()
                }

                if (alreadyStoredOptimizedImage != null) {
                    call.respondJson<CheckImageResponse>(
                        ImageExistsResponse(
                            alreadyStoredOptimizedImage[StoredImages.id].value,
                            Hex.encodeHexString(alreadyStoredOptimizedImage[StoredImages.shaHash]),
                            Hex.encodeHexString(alreadyStoredOptimizedImage[StoredImages.originalShaHash]),
                        )
                    )
                    return@withContext
                }
            }

            call.respondJson<CheckImageResponse>(
                ImageDoesNotExistResponse()
            )
        }
    }
}