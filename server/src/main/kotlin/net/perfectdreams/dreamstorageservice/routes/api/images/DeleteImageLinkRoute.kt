package net.perfectdreams.dreamstorageservice.routes.api.images

import io.ktor.application.*
import io.ktor.request.*
import mu.KotlinLogging
import net.perfectdreams.dreamstorageservice.DreamStorageService
import net.perfectdreams.dreamstorageservice.data.api.DeleteImageLinkRequest
import net.perfectdreams.dreamstorageservice.entities.AuthorizationToken
import net.perfectdreams.dreamstorageservice.routes.api.RequiresAPIAuthenticationRoute
import net.perfectdreams.dreamstorageservice.tables.ImageLinks
import net.perfectdreams.dreamstorageservice.utils.ktor.respondJson
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class DeleteImageLinkRoute(m: DreamStorageService) : RequiresAPIAuthenticationRoute(m, "/images/links") {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override suspend fun onAuthenticatedRequest(call: ApplicationCall, token: AuthorizationToken) {
        val request = Json.decodeFromString<DeleteImageLinkRequest>(call.receiveText())

        m.transaction {
            val fileLink = ImageLinks.select {
                ImageLinks.createdBy eq token.id and (ImageLinks.folder eq request.folder) and (ImageLinks.file eq request.file)
            }.firstOrNull() ?: return@transaction

            val storedFile = fileLink[ImageLinks.storedImage]

            ImageLinks.deleteWhere { ImageLinks.id eq fileLink[ImageLinks.id] }

            // Automatically clean up files that do not have any links pointing to them
            m.checkAndCleanUpImage(storedFile.value)
        }

        call.respondJson("")
    }
}