package net.perfectdreams.dreamstorageservice.client

import io.ktor.client.*
import net.perfectdreams.dreamstorageservice.client.services.FileLinkService

class DreamStorageServiceClient(baseUrl: String, val token: String, val http: HttpClient) {
    val baseUrl = baseUrl.removeSuffix("/") // Remove trailing slash

    val fileLinks = FileLinkService(this)
}