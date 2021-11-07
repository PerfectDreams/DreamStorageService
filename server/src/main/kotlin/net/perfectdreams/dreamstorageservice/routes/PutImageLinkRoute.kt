package net.perfectdreams.dreamstorageservice.routes

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import net.perfectdreams.dreamstorageservice.DreamStorageService
import net.perfectdreams.dreamstorageservice.data.CreateImageLinkRequest
import net.perfectdreams.dreamstorageservice.data.CreateImageLinkResponse
import net.perfectdreams.dreamstorageservice.data.LinkInfo
import net.perfectdreams.dreamstorageservice.entities.AuthorizationToken
import net.perfectdreams.dreamstorageservice.entities.ImageLink
import net.perfectdreams.dreamstorageservice.tables.StoredImages
import net.perfectdreams.dreamstorageservice.utils.ktor.respondJson
import org.jetbrains.exposed.sql.select
import java.time.Instant

class PutImageLinkRoute(m: DreamStorageService) : RequiresAPIAuthenticationRoute(m, "/api/v1/images/links") {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override suspend fun onAuthenticatedRequest(call: ApplicationCall, token: AuthorizationToken) {
        val request = Json.decodeFromString<CreateImageLinkRequest>(call.receiveText())
        val imageId = request.imageId

        val imageLinks = m.transaction {
            val alreadyStoredImage = StoredImages.slice(StoredImages.id)
                .select {
                    StoredImages.id eq imageId
                }.firstOrNull() ?: run {
                return@transaction null
            }

            request.links.map {
                ImageLink.new {
                    folder = it.folder
                    file = it.file
                    createdAt = Instant.now()
                    createdBy = token.id
                    storedImageId = alreadyStoredImage[StoredImages.id]
                }
            }
        } ?: run {
            call.respondText("", status = HttpStatusCode.NotFound)
            return
        }

        call.respondJson(
            CreateImageLinkResponse(
                imageLinks.map {
                    CreateImageLinkResponse.ImageLink(
                        it.id.value,
                        LinkInfo(
                            it.folder,
                            it.file
                        )
                    )
                }
            )
        )
    }
}