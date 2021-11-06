package net.perfectdreams.dreamstorageservice.routes

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.perfectdreams.dreamstorageservice.DreamStorageService
import net.perfectdreams.dreamstorageservice.data.AddAllowedImageCropRequest
import net.perfectdreams.dreamstorageservice.entities.AllowedImageCrop
import net.perfectdreams.dreamstorageservice.entities.AuthorizationToken
import net.perfectdreams.dreamstorageservice.tables.StoredFiles
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import java.time.Instant

class PutAllowedImageCropOnFileRoute(m: DreamStorageService) : RequiresAPIAuthenticationRoute(m, "/api/v1/files/{fileId}/allowed-crops") {
    override suspend fun onAuthenticatedRequest(call: ApplicationCall, token: AuthorizationToken) {
        val fileId = call.parameters.getOrFail("fileId").toLongOrNull()
        val request = Json.decodeFromString<AddAllowedImageCropRequest>(call.receiveText())

        m.transaction {
            val file = StoredFiles.slice(StoredFiles.id)
                .select {
                    StoredFiles.id eq fileId and (StoredFiles.createdBy eq token.id)
                }.firstOrNull()

            if (file == null) {
                call.respondText("", status = HttpStatusCode.NotFound)
                return@transaction
            }

            AllowedImageCrop.new {
                this.cropWidth = request.cropWidth
                this.cropHeight = request.cropHeight
                this.cropX = request.cropX
                this.cropY = request.cropY
                this.createdAt = Instant.now()
                this.storedFileId = file[StoredFiles.id]
            }
        }


        call.respondText("", status = HttpStatusCode.NoContent)
    }
}