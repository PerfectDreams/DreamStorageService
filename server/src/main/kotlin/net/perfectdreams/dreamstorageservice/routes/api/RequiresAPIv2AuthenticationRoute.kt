package net.perfectdreams.dreamstorageservice.routes.api

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import mu.KotlinLogging
import net.perfectdreams.dreamstorageservice.DreamStorageService
import net.perfectdreams.dreamstorageservice.entities.AuthorizationToken
import net.perfectdreams.dreamstorageservice.tables.AuthorizationTokens
import org.jetbrains.exposed.sql.select

abstract class RequiresAPIv2AuthenticationRoute(val m: DreamStorageService, path: String) : VersionedAPIv2Route(path) {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    abstract suspend fun onAuthenticatedRequest(call: ApplicationCall, token: AuthorizationToken)

    override suspend fun onRequest(call: ApplicationCall) {
        val auth = call.request.header("Authorization")
        val clazzName = this::class.simpleName

        if (auth == null) {
            logger.warn { "Someone tried to access $path (${clazzName}) but the Authorization header was missing!" }
            call.respondText("", status = HttpStatusCode.Unauthorized)
            return
        }

        val validKey = m.transaction {
            AuthorizationTokens.select { AuthorizationTokens.token eq auth }.firstOrNull()
        }

        logger.trace { "$auth is trying to access $path (${clazzName}), using key $validKey" }
        if (validKey != null) {
            onAuthenticatedRequest(
                call,
                AuthorizationToken(
                    validKey[AuthorizationTokens.id],
                    validKey[AuthorizationTokens.token],
                    validKey[AuthorizationTokens.description],
                    validKey[AuthorizationTokens.namespace]
                )
            )
        } else {
            logger.warn { "$auth was rejected when trying to access $path ($clazzName)!" }
            call.respondText("", status = HttpStatusCode.Unauthorized)
        }
    }
}