package net.perfectdreams.dreamstorageservice.client

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.perfectdreams.dreamstorageservice.data.AllowedImageCropsListRequest
import net.perfectdreams.dreamstorageservice.data.DeleteFileLinkRequest
import net.perfectdreams.dreamstorageservice.data.GetNamespaceResponse
import net.perfectdreams.dreamstorageservice.data.UploadFileRequest
import net.perfectdreams.dreamstorageservice.data.UploadImageRequest
import net.perfectdreams.dreamstorageservice.data.UploadRequest

class DreamStorageServiceClient(baseUrl: String, val token: String, val http: HttpClient) {
    companion object {
        private const val apiVersion = "v1"
    }
    val baseUrl = baseUrl.removeSuffix("/") // Remove trailing slash

    private var cachedNamespace: String? = null
    private val mutex = Mutex()

    // ===[ NAMESPACES ]===
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

    // ===[ FILE UPLOAD ]===
    suspend fun uploadFile(
        data: ByteArray,
        mimeType: ContentType,
        request: UploadFileRequest
    ) = uploadGeneric(
        data,
        "file",
        mimeType,
        request
    )

    suspend fun uploadImage(
        data: ByteArray,
        mimeType: ContentType,
        request: UploadImageRequest
    ) = uploadGeneric(
        data,
        "image",
        mimeType,
        request
    )

    private suspend inline fun <reified T : UploadRequest> uploadGeneric(
        data: ByteArray,
        type: String,
        mimeType: ContentType,
        request: T
    ): T {
        val parts = formData {
            append("attributes", Json.encodeToString(request))

            append(
                "file",
                data,
                Headers.build {
                    append(HttpHeaders.ContentType, mimeType.toString())
                    append(HttpHeaders.ContentDisposition, "filename=${request.path.substringAfterLast("/")}")
                }
            )
        }

        val response = http.submitFormWithBinaryData<HttpResponse>("${baseUrl}/api/$apiVersion/upload/$type", formData = parts) {
            this.method = HttpMethod.Put

            header("Authorization", token)
        }

        return Json.decodeFromString(response.readText())
    }

    // ===[ LINKS ]===
    suspend fun deleteLink(
        request: DeleteFileLinkRequest
    ) {
        http.delete<HttpResponse>("${baseUrl}/api/$apiVersion/links") {
            this.body = Json.encodeToString(request)

            header("Authorization", token)
        }
    }

    // ===[ IMAGE CROPS ]===
    suspend fun setAllowedImageCrops(
        fileId: Long,
        request: AllowedImageCropsListRequest
    ) {
        http.put<HttpResponse>("${baseUrl}/api/$apiVersion/files/${fileId}/allowed-crops") {
            this.body = Json.encodeToString(request)

            header("Authorization", token)
        }
    }
}