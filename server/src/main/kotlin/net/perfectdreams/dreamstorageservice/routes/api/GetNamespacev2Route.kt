package net.perfectdreams.dreamstorageservice.routes.api

import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.perfectdreams.dreamstorageservice.DreamStorageService
import net.perfectdreams.dreamstorageservice.data.api.GetNamespaceResponse
import net.perfectdreams.dreamstorageservice.entities.AuthorizationToken

class GetNamespacev2Route(m: DreamStorageService) : RequiresAPIv2AuthenticationRoute(m, "/namespace") {
    override suspend fun onAuthenticatedRequest(call: ApplicationCall, token: AuthorizationToken) {
        call.respondText(Json.encodeToString(GetNamespaceResponse(token.namespace)))
    }
}