package net.perfectdreams.dreamstorageservice.routes

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import net.perfectdreams.dreamstorageservice.DreamStorageService
import net.perfectdreams.dreamstorageservice.entities.FileLink
import net.perfectdreams.sequins.ktor.BaseRoute

class GetFileFromFileLinkRoute(val m: DreamStorageService) : BaseRoute("/{path...}") {
    override suspend fun onRequest(call: ApplicationCall) {
        val path = call.parameters.getAll("path")!!
        val storedFile = m.transaction {
            FileLink.findById(path.joinToString("/"))?.storedFile
        }
        if (storedFile == null) {
            call.respondText("", status = HttpStatusCode.NotFound)
        } else {
            call.respondBytes(storedFile.data, ContentType.parse(storedFile.mimeType))
        }
    }
}