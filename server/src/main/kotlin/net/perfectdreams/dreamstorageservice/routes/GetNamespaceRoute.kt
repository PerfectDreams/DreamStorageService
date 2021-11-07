package net.perfectdreams.dreamstorageservice.routes

import io.ktor.application.*
import io.ktor.response.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.perfectdreams.dreamstorageservice.DreamStorageService
import net.perfectdreams.dreamstorageservice.data.GetNamespaceResponse
import net.perfectdreams.dreamstorageservice.entities.AuthorizationToken

class GetNamespaceRoute(m: DreamStorageService) : RequiresAPIAuthenticationRoute(m, "/api/v1/namespace") {
    override suspend fun onAuthenticatedRequest(call: ApplicationCall, token: AuthorizationToken) {
        call.respondText(Json.encodeToString(GetNamespaceResponse(token.namespace)))
    }
}