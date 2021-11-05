package net.perfectdreams.dreamstorageservice.routes

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import net.perfectdreams.dreamstorageservice.DreamStorageService
import net.perfectdreams.dreamstorageservice.data.DeleteFileLinkRequest
import net.perfectdreams.dreamstorageservice.entities.AuthorizationToken
import net.perfectdreams.dreamstorageservice.entities.FileLink

class DeleteFileLinkRoute(m: DreamStorageService) : RequiresAPIAuthenticationRoute(m, "/api/v1/delete") {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override suspend fun onAuthenticatedRequest(call: ApplicationCall, token: AuthorizationToken) {
        val request = Json.decodeFromString<DeleteFileLinkRequest>(call.receiveText())
        val path = request.path

        if (!path.startsWith(token.allowedFilePath)) {
            logger.warn { "Token \"${token.description}\" tried to upload file at $path but they aren't allowed to do that!" }
            call.respondText("", status = HttpStatusCode.Unauthorized)
            return
        }

        m.transaction {
            val fileLink = FileLink.findById(path)
            val storedFileId = fileLink?.storedFileId
            fileLink?.delete()

            if (storedFileId != null)
                m.checkAndCleanUpFile(storedFileId.value)
        }

        logger.info { "Deleted file link $path" }
    }
}