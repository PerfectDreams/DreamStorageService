package net.perfectdreams.dreamstorageservice.client

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.perfectdreams.dreamstorageservice.client.services.FileLinkService
import net.perfectdreams.dreamstorageservice.data.GetNamespaceResponse

class DreamStorageServiceClient(baseUrl: String, val token: String, val http: HttpClient) {
    val baseUrl = baseUrl.removeSuffix("/") // Remove trailing slash

    val fileLinks = FileLinkService(this)

    private var cachedNamespace: String? = null
    private val mutex = Mutex()

    suspend fun getCachedNamespaceOrRetrieve(): String {
        mutex.withLock {
            val cachedNamespace = cachedNamespace
            if (cachedNamespace != null)
                return cachedNamespace

            val namespace = getNamespace()
            this.cachedNamespace = namespace
            return namespace
        }
    }

    suspend fun getNamespace(): String {
        val response = http.get<HttpResponse>("$baseUrl/api/v1/namespace") {
            header("Authorization", token)
        }

        return Json.decodeFromString<GetNamespaceResponse>(response.readText()).namespace
    }
}