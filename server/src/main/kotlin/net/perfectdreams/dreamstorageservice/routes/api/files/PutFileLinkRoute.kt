package net.perfectdreams.dreamstorageservice.routes.api.files

import io.ktor.server.application.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import net.perfectdreams.dreamstorageservice.DreamStorageService
import net.perfectdreams.dreamstorageservice.data.FileLinkInfo
import net.perfectdreams.dreamstorageservice.data.api.CreateFileLinkRequest
import net.perfectdreams.dreamstorageservice.data.api.CreateFileLinkResponse
import net.perfectdreams.dreamstorageservice.entities.AuthorizationToken
import net.perfectdreams.dreamstorageservice.entities.FileLink
import net.perfectdreams.dreamstorageservice.routes.api.RequiresAPIv2AuthenticationRoute
import net.perfectdreams.dreamstorageservice.tables.FileLinks
import net.perfectdreams.dreamstorageservice.tables.StoredFiles
import net.perfectdreams.dreamstorageservice.utils.ktor.respondJson
import org.apache.commons.codec.binary.Hex
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import java.time.Instant

class PutFileLinkRoute(m: DreamStorageService) : RequiresAPIv2AuthenticationRoute(m, "/files/links") {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override suspend fun onAuthenticatedRequest(call: ApplicationCall, token: AuthorizationToken) {
        val request = Json.decodeFromString<CreateFileLinkRequest>(call.receiveText())
        val fileId = request.fileId

        val fileLink = m.transaction {
            val alreadyStoredFile = StoredFiles.slice(StoredFiles.id, StoredFiles.shaHash)
                .select {
                    StoredFiles.id eq fileId
                }.firstOrNull() ?: run {
                return@transaction null
            }

            val shaHashAsString = Hex.encodeHexString(alreadyStoredFile[StoredFiles.shaHash])

            val formattedFolder = request.folder.format(shaHashAsString)
            val formattedFile = request.file.format(shaHashAsString)

            val alreadyCreatedLink = FileLink.find { FileLinks.createdBy eq token.id and (FileLinks.folder eq formattedFolder) and (FileLinks.file eq formattedFile) }
                .firstOrNull()

            // There's already a link created with that name! Let's just return the already created link
            if (alreadyCreatedLink?.createdBy == token.id && alreadyCreatedLink.storedFileId == alreadyStoredFile[StoredFiles.id])
                return@transaction alreadyCreatedLink

            // There's already a link created with that name but the file doesn't match! Let's delete it!!
            alreadyCreatedLink?.delete()

            FileLink.new {
                folder = formattedFolder
                file = formattedFile
                createdAt = Instant.now()
                createdBy = token.id
                storedFileId = alreadyStoredFile[StoredFiles.id]
            }
        } ?: run {
            call.respondText("", status = HttpStatusCode.NotFound)
            return
        }

        call.respondJson(
            CreateFileLinkResponse(
                FileLinkInfo(
                    fileLink.id.value,
                    fileLink.storedFileId.value,
                    fileLink.folder,
                    fileLink.file
                )
            )
        )
    }
}