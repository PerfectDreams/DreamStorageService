package net.perfectdreams.dreamstorageservice.routes

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import net.perfectdreams.dreamstorageservice.DreamStorageService
import net.perfectdreams.dreamstorageservice.data.CreateFileLinkRequest
import net.perfectdreams.dreamstorageservice.data.CreateFileLinkResponse
import net.perfectdreams.dreamstorageservice.data.CreateImageLinkResponse
import net.perfectdreams.dreamstorageservice.data.LinkInfo
import net.perfectdreams.dreamstorageservice.entities.AuthorizationToken
import net.perfectdreams.dreamstorageservice.entities.FileLink
import net.perfectdreams.dreamstorageservice.tables.StoredFiles
import net.perfectdreams.dreamstorageservice.utils.ktor.respondJson
import org.apache.commons.codec.binary.Hex
import org.jetbrains.exposed.sql.select
import java.time.Instant

class PutFileLinkRoute(m: DreamStorageService) : RequiresAPIAuthenticationRoute(m, "/api/v1/files/links") {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override suspend fun onAuthenticatedRequest(call: ApplicationCall, token: AuthorizationToken) {
        val request = Json.decodeFromString<CreateFileLinkRequest>(call.receiveText())
        val fileId = request.fileId

        val fileLinks = m.transaction {
            val alreadyStoredFile = StoredFiles.slice(StoredFiles.id, StoredFiles.shaHash)
                .select {
                    StoredFiles.id eq fileId
                }.firstOrNull() ?: run {
                return@transaction null
            }

            val shaHashAsString = Hex.encodeHexString(alreadyStoredFile[StoredFiles.shaHash])

            request.links.map {
                FileLink.new {
                    folder = it.folder.format(shaHashAsString)
                    file = it.file.format(shaHashAsString)
                    createdAt = Instant.now()
                    createdBy = token.id
                    storedFileId = alreadyStoredFile[StoredFiles.id]
                }
            }
        } ?: run {
            call.respondText("", status = HttpStatusCode.NotFound)
            return
        }

        call.respondJson(
            CreateFileLinkResponse(
                fileLinks.map {
                    CreateFileLinkResponse.FileLink(
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