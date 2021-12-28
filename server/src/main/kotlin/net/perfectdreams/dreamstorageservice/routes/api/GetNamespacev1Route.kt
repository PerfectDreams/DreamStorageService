package net.perfectdreams.dreamstorageservice.routes.api

import io.ktor.application.*
import io.ktor.response.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.perfectdreams.dreamstorageservice.DreamStorageService
import net.perfectdreams.dreamstorageservice.data.api.GetNamespaceResponse
import net.perfectdreams.dreamstorageservice.entities.AuthorizationToken

// TODO: This can be removed later, just a smol workaround because the client was still querying the API v1
class GetNamespacev1Route(m: DreamStorageService) : RequiresAPIv1AuthenticationRoute(m, "/namespace") {
    override suspend fun onAuthenticatedRequest(call: ApplicationCall, token: AuthorizationToken) {
        call.respondText(Json.encodeToString(GetNamespaceResponse(token.namespace)))
    }
}