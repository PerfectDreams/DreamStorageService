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
import net.perfectdreams.dreamstorageservice.data.DeleteImageLinkRequest
import net.perfectdreams.dreamstorageservice.entities.AuthorizationToken
import net.perfectdreams.dreamstorageservice.entities.ImageLink
import net.perfectdreams.dreamstorageservice.tables.FileLinks
import net.perfectdreams.dreamstorageservice.tables.ImageLinks
import net.perfectdreams.dreamstorageservice.tables.StoredImages
import net.perfectdreams.dreamstorageservice.utils.ktor.respondJson
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import java.time.Instant

class DeleteImageLinkRoute(m: DreamStorageService) : RequiresAPIAuthenticationRoute(m, "/api/v1/images/links") {
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