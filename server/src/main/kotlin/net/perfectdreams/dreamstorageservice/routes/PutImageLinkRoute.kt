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
import net.perfectdreams.dreamstorageservice.tables.FileLinks
import net.perfectdreams.dreamstorageservice.tables.ImageLinks
import net.perfectdreams.dreamstorageservice.tables.StoredFiles
import net.perfectdreams.dreamstorageservice.tables.StoredImages
import net.perfectdreams.dreamstorageservice.utils.ktor.respondJson
import org.apache.commons.codec.binary.Hex
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import java.time.Instant

class PutImageLinkRoute(m: DreamStorageService) : RequiresAPIAuthenticationRoute(m, "/api/v1/images/links") {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override suspend fun onAuthenticatedRequest(call: ApplicationCall, token: AuthorizationToken) {
        val request = Json.decodeFromString<CreateImageLinkRequest>(call.receiveText())
        val imageId = request.imageId

        val imageLink = m.transaction {
            val alreadyStoredImage = StoredImages.slice(StoredImages.id, StoredImages.shaHash)
                .select {
                    StoredImages.id eq imageId
                }.firstOrNull() ?: run {
                return@transaction null
            }

            val shaHashAsString = Hex.encodeHexString(alreadyStoredImage[StoredImages.shaHash])

            val formattedFolder = request.folder.format(shaHashAsString)
            val formattedFile = request.file.format(shaHashAsString)

            val alreadyCreatedLink = ImageLink.find { ImageLinks.createdBy eq token.id and (ImageLinks.folder eq formattedFolder) and (ImageLinks.file eq formattedFile) }
                .firstOrNull()

            // There's already a link created with that name! Let's just return the already created link
            if (alreadyCreatedLink?.createdBy == token.id && alreadyCreatedLink.storedImageId == alreadyStoredImage[StoredImages.id])
                return@transaction alreadyCreatedLink

            // There's already a link created with that name but the file doesn't match! Let's delete it!!
            alreadyCreatedLink?.delete()

            ImageLink.new {
                folder = formattedFolder
                file = formattedFile
                createdAt = Instant.now()
                createdBy = token.id
                storedImageId = alreadyStoredImage[StoredImages.id]
            }
        } ?: run {
            call.respondText("", status = HttpStatusCode.NotFound)
            return
        }

        call.respondJson(
            CreateImageLinkResponse(
                imageLink.id.value,
                imageLink.folder,
                imageLink.file
            )
        )
    }
}