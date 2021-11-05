package net.perfectdreams.dreamstorageservice.routes

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import mu.KotlinLogging
import net.perfectdreams.dreamstorageservice.DreamStorageService
import net.perfectdreams.dreamstorageservice.entities.FileLink
import net.perfectdreams.sequins.ktor.BaseRoute

class GetFileFromFileLinkRoute(val m: DreamStorageService) : BaseRoute("/{path...}") {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override suspend fun onRequest(call: ApplicationCall) {
        val path = call.parameters.getAll("path")!!
        val storedFile = m.transaction {
            FileLink.findById(path.joinToString("/"))?.storedFile
        }
        if (storedFile == null) {
            logger.info { "User requested file in link $path, but the file doesn't exist!" }
            call.respondText("", status = HttpStatusCode.NotFound)
        } else {
            logger.info { "User requested file in link $path" }
            call.respondBytes(storedFile.data, ContentType.parse(storedFile.mimeType))
        }
    }
}