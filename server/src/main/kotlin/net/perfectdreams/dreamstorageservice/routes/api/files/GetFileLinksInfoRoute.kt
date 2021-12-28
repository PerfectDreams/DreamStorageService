package net.perfectdreams.dreamstorageservice.routes.api.files

import io.ktor.application.*
import mu.KotlinLogging
import net.perfectdreams.dreamstorageservice.DreamStorageService
import net.perfectdreams.dreamstorageservice.data.FileLinkInfo
import net.perfectdreams.dreamstorageservice.entities.AuthorizationToken
import net.perfectdreams.dreamstorageservice.entities.FileLink
import net.perfectdreams.dreamstorageservice.routes.api.RequiresAPIv2AuthenticationRoute
import net.perfectdreams.dreamstorageservice.tables.FileLinks
import net.perfectdreams.dreamstorageservice.utils.ktor.respondJson

class GetFileLinksInfoRoute(m: DreamStorageService) : RequiresAPIv2AuthenticationRoute(m, "/file/links") {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override suspend fun onAuthenticatedRequest(call: ApplicationCall, token: AuthorizationToken) {
        val fileLinks = m.transaction {
            FileLink.find { FileLinks.createdBy eq token.id }
                .map {
                    FileLinkInfo(
                        it.id.value,
                        it.storedFileId.value,
                        it.folder,
                        it.file
                    )
                }
        }

        call.respondJson(fileLinks)
    }
}