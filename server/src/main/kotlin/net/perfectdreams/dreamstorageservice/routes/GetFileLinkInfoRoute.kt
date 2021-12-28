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
import net.perfectdreams.dreamstorageservice.data.FileLinkInfoResponse
import net.perfectdreams.dreamstorageservice.data.ImageLinkInfoResponse
import net.perfectdreams.dreamstorageservice.data.LinkInfo
import net.perfectdreams.dreamstorageservice.entities.AuthorizationToken
import net.perfectdreams.dreamstorageservice.entities.FileLink
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

class GetFileLinkInfoRoute(m: DreamStorageService) : RequiresAPIAuthenticationRoute(m, "/api/v1/files/links/{folderWithFile...}") {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override suspend fun onAuthenticatedRequest(call: ApplicationCall, token: AuthorizationToken) {
        val folderWithFile = call.parameters.getAll("folderWithFile") ?: error("folderWithFile not present!")
        val folder = folderWithFile.dropLast(1).joinToString("/")
        val file = folderWithFile.last()

        val imageLink = m.transaction {
            FileLink.find {
                FileLinks.createdBy eq token.id and (FileLinks.folder eq folder and (FileLinks.file eq file))
            }.firstOrNull()
        } ?: run {
            call.respondText("", status = HttpStatusCode.NotFound)
            return
        }

        call.respondJson(
            FileLinkInfoResponse(
                imageLink.id.value,
                imageLink.storedFileId.value,
                imageLink.folder,
                imageLink.file
            )
        )
    }
}