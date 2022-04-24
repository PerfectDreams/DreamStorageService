package net.perfectdreams.dreamstorageservice.routes.api.files

import io.ktor.server.application.*
import io.ktor.server.request.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import net.perfectdreams.dreamstorageservice.DreamStorageService
import net.perfectdreams.dreamstorageservice.data.api.DeleteFileLinkRequest
import net.perfectdreams.dreamstorageservice.entities.AuthorizationToken
import net.perfectdreams.dreamstorageservice.routes.api.RequiresAPIv2AuthenticationRoute
import net.perfectdreams.dreamstorageservice.tables.FileLinks
import net.perfectdreams.dreamstorageservice.utils.ktor.respondJson
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select

class DeleteFileLinkRoute(m: DreamStorageService) : RequiresAPIv2AuthenticationRoute(m, "/files/links") {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override suspend fun onAuthenticatedRequest(call: ApplicationCall, token: AuthorizationToken) {
        val request = Json.decodeFromString<DeleteFileLinkRequest>(call.receiveText())

        m.transaction {
            val fileLink = FileLinks.select {
                FileLinks.createdBy eq token.id and (FileLinks.folder eq request.folder) and (FileLinks.file eq request.file)
            }.firstOrNull() ?: return@transaction

            val storedFile = fileLink[FileLinks.storedFile]

            FileLinks.deleteWhere { FileLinks.id eq fileLink[FileLinks.id] }

            // Automatically clean up files that do not have any links pointing to them
            m.checkAndCleanUpFile(storedFile.value)
        }

        call.respondJson("")
    }
}