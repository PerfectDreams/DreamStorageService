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
import net.perfectdreams.dreamstorageservice.data.DeleteFileLinkRequest
import net.perfectdreams.dreamstorageservice.data.DeleteImageLinkRequest
import net.perfectdreams.dreamstorageservice.entities.AuthorizationToken
import net.perfectdreams.dreamstorageservice.entities.ImageLink
import net.perfectdreams.dreamstorageservice.tables.FileLinks
import net.perfectdreams.dreamstorageservice.tables.ImageLinks
import net.perfectdreams.dreamstorageservice.tables.StoredImages
import net.perfectdreams.dreamstorageservice.utils.ktor.respondJson
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import java.time.Instant

class DeleteFileLinkRoute(m: DreamStorageService) : RequiresAPIAuthenticationRoute(m, "/api/v1/files/links") {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override suspend fun onAuthenticatedRequest(call: ApplicationCall, token: AuthorizationToken) {
        val request = Json.decodeFromString<DeleteFileLinkRequest>(call.receiveText())

        m.transaction {
            val linksThatShouldBeDeleted = mutableListOf<ResultRow>()

            for (link in request.links) {
                linksThatShouldBeDeleted.addAll(
                    FileLinks.select {
                        FileLinks.createdBy eq token.id and (FileLinks.folder eq link.folder) and (FileLinks.file eq link.file)
                    }
                )
            }

            val affectedImageIds = linksThatShouldBeDeleted.map { it[FileLinks.storedFile].value }
                .distinct()

            FileLinks.deleteWhere { FileLinks.id inList linksThatShouldBeDeleted.map { it[FileLinks.id] }}

            // Automatically clean up images that do not have any links pointing to them
            affectedImageIds.forEach {
                m.checkAndCleanUpFile(it)
            }
        }

        call.respondJson("")
    }
}