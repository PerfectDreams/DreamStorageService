package net.perfectdreams.dreamstorageservice.utils.ktor

import io.ktor.server.application.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.response.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

suspend fun ApplicationCall.respondJson(
    text: String,
    status: HttpStatusCode? = null,
    configure: OutgoingContent.() -> Unit = {}
) = respondText(text, ContentType.Application.Json, status, configure)

suspend inline fun <reified T> ApplicationCall.respondJson(
    serializableObject: T,
    status: HttpStatusCode? = null,
    noinline configure: OutgoingContent.() -> Unit = {}
) = respondText(Json.encodeToString(serializableObject), ContentType.Application.Json, status, configure)