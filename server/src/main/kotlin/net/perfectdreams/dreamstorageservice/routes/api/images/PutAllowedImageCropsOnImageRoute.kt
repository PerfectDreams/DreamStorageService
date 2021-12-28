package net.perfectdreams.dreamstorageservice.routes.api.images

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.*
import net.perfectdreams.dreamstorageservice.DreamStorageService
import net.perfectdreams.dreamstorageservice.entities.AuthorizationToken
import net.perfectdreams.dreamstorageservice.routes.api.RequiresAPIAuthenticationRoute
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.perfectdreams.dreamstorageservice.data.api.AllowedImageCropsListRequest
import net.perfectdreams.dreamstorageservice.data.Crop
import net.perfectdreams.dreamstorageservice.entities.AllowedImageCrop
import net.perfectdreams.dreamstorageservice.tables.AllowedImageCrops
import net.perfectdreams.dreamstorageservice.tables.StoredImages
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import java.time.Instant

class PutAllowedImageCropsOnImageRoute(m: DreamStorageService) : RequiresAPIAuthenticationRoute(m, "/images/{imageId}/allowed-crops") {
    override suspend fun onAuthenticatedRequest(call: ApplicationCall, token: AuthorizationToken) {
        val imageId = call.parameters.getOrFail("imageId").toLongOrNull()
        val request = Json.decodeFromString<AllowedImageCropsListRequest>(call.receiveText())

        m.transaction {
            val file = StoredImages.slice(StoredImages.id)
                .select {
                    StoredImages.id eq imageId and (StoredImages.createdBy eq token.id)
                }.firstOrNull()

            if (file == null) {
                call.respondText("", status = HttpStatusCode.NotFound)
                return@transaction
            }

            val currentCrops = AllowedImageCrop.find {
                AllowedImageCrops.id eq file[StoredImages.id]
            }.map {
                Crop(it.cropX, it.cropY, it.cropWidth, it.cropHeight)
            }

            for (crop in request.crops.filter { it !in currentCrops }) {
                AllowedImageCrop.new {
                    this.cropWidth = crop.width
                    this.cropHeight = crop.height
                    this.cropX = crop.x
                    this.cropY = crop.y
                    this.createdAt = Instant.now()
                    this.storedImageId = file[StoredImages.id]
                }
            }
        }

        call.respondText("", status = HttpStatusCode.NoContent)
    }
}