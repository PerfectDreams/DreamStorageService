package net.perfectdreams.dreamstorageservice.routes.api.images

import io.ktor.application.*
import mu.KotlinLogging
import net.perfectdreams.dreamstorageservice.DreamStorageService
import net.perfectdreams.dreamstorageservice.data.ImageLinkInfo
import net.perfectdreams.dreamstorageservice.entities.AuthorizationToken
import net.perfectdreams.dreamstorageservice.entities.ImageLink
import net.perfectdreams.dreamstorageservice.routes.api.RequiresAPIv2AuthenticationRoute
import net.perfectdreams.dreamstorageservice.tables.ImageLinks
import net.perfectdreams.dreamstorageservice.utils.ktor.respondJson

class GetImageLinksInfoRoute(m: DreamStorageService) : RequiresAPIv2AuthenticationRoute(m, "/images/links") {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override suspend fun onAuthenticatedRequest(call: ApplicationCall, token: AuthorizationToken) {
        val imageLinks = m.transaction {
            ImageLink.find { ImageLinks.createdBy eq token.id }
                .map {
                    ImageLinkInfo(
                        it.id.value,
                        it.storedImageId.value,
                        it.folder,
                        it.file
                    )
                }
        }

        call.respondJson(imageLinks)
    }
}