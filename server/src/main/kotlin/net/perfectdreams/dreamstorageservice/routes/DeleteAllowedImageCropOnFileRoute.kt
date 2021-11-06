package net.perfectdreams.dreamstorageservice.routes

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.perfectdreams.dreamstorageservice.DreamStorageService
import net.perfectdreams.dreamstorageservice.data.DeleteAllowedImageCropRequest
import net.perfectdreams.dreamstorageservice.entities.AuthorizationToken
import net.perfectdreams.dreamstorageservice.tables.AllowedImageCrops
import net.perfectdreams.dreamstorageservice.tables.StoredFiles
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select

class DeleteAllowedImageCropOnFileRoute(m: DreamStorageService) : RequiresAPIAuthenticationRoute(m, "/api/v1/allowed-crops") {
    override suspend fun onAuthenticatedRequest(call: ApplicationCall, token: AuthorizationToken) {
        val request = Json.decodeFromString<DeleteAllowedImageCropRequest>(call.receiveText())
        val fileId = request.fileId

        m.transaction {
            val file = StoredFiles.slice(StoredFiles.id)
                .select {
                    StoredFiles.id eq fileId and (StoredFiles.createdBy eq token.id)
                }.firstOrNull()

            if (file == null) {
                call.respondText("", status = HttpStatusCode.NotFound)
                return@transaction
            }

            AllowedImageCrops.deleteWhere {
                AllowedImageCrops.storedFile eq file[AllowedImageCrops.id] and (
                        AllowedImageCrops.cropWidth eq request.cropWidth and
                                (AllowedImageCrops.cropHeight eq request.cropHeight) and
                                (AllowedImageCrops.cropX eq request.cropX) and
                                (AllowedImageCrops.cropY eq request.cropY)
                        )
            }
        }

        call.respondText("", status = HttpStatusCode.NoContent)
    }
}