package net.perfectdreams.dreamstorageservice.routes.api.images

import io.ktor.application.*
import io.ktor.http.content.*
import io.ktor.request.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import net.perfectdreams.dreamstorageservice.DreamStorageService
import net.perfectdreams.dreamstorageservice.data.ImageInfo
import net.perfectdreams.dreamstorageservice.data.api.UploadImageRequest
import net.perfectdreams.dreamstorageservice.data.api.UploadImageResponse
import net.perfectdreams.dreamstorageservice.entities.AuthorizationToken
import net.perfectdreams.dreamstorageservice.entities.StoredImage
import net.perfectdreams.dreamstorageservice.routes.api.RequiresAPIv2AuthenticationRoute
import net.perfectdreams.dreamstorageservice.tables.StoredImages
import net.perfectdreams.dreamstorageservice.utils.ktor.respondJson
import org.apache.commons.codec.binary.Hex
import org.jetbrains.exposed.sql.select
import java.time.Instant

class PostUploadImageRoute(m: DreamStorageService) : RequiresAPIv2AuthenticationRoute(m, "/images") {
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

            val attributes = Json.decodeFromString<UploadImageRequest>(attributesPart.value)

            val fileToBeStored = filePart.streamProvider.invoke().readAllBytes()
            val contentType = filePart.contentType ?: error("Missing Content-Type!")

            if (contentType.contentType != "image")
                error("Trying to upload a non-image file via the upload image endpoint!")

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
                call.respondJson(
                    UploadImageResponse(
                        false,
                        ImageInfo(
                            alreadyStoredImage[StoredImages.id].value,
                            Hex.encodeHexString(alreadyStoredImage[StoredImages.shaHash]),
                            Hex.encodeHexString(alreadyStoredImage[StoredImages.originalShaHash])
                        )
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
                    call.respondJson(
                        UploadImageResponse(
                            false,
                            ImageInfo(
                                alreadyStoredOptimizedImage[StoredImages.id].value,
                                Hex.encodeHexString(alreadyStoredOptimizedImage[StoredImages.shaHash]),
                                Hex.encodeHexString(alreadyStoredOptimizedImage[StoredImages.originalShaHash])
                            )
                        )
                    )
                    return@withContext
                }
            }

            // Okay, so none of the variations of the image exist, so let's upload it!
            val storedImage = m.transaction {
                StoredImage.new {
                    this.mimeType = contentType.toString()
                    this.shaHash = checksum
                    this.originalShaHash = originalChecksum
                    this.uploadedAt = Instant.now()
                    this.createdBy = token.id
                    this.data = trueContentsToBeStored
                }
            }

            logger.info { "Uploaded image ${storedImage.id.value}" }

            call.respondJson(
                UploadImageResponse(
                    true,
                    ImageInfo(
                        storedImage.id.value,
                        Hex.encodeHexString(storedImage.shaHash),
                        Hex.encodeHexString(storedImage.originalShaHash)
                    )
                )
            )
        }
    }
}