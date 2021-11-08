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
import net.perfectdreams.dreamstorageservice.data.CreateFileLinkRequest
import net.perfectdreams.dreamstorageservice.data.CreateImageLinkRequest
import net.perfectdreams.dreamstorageservice.data.CreateImageLinkResponse
import net.perfectdreams.dreamstorageservice.data.DeleteFileLinkRequest
import net.perfectdreams.dreamstorageservice.data.DeleteImageLinkRequest
import net.perfectdreams.dreamstorageservice.data.GetNamespaceResponse
import net.perfectdreams.dreamstorageservice.data.UploadFileResponse
import net.perfectdreams.dreamstorageservice.data.UploadImageRequest

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
        mimeType: ContentType
    ) = uploadGeneric<Unit, UploadFileResponse>(
        data,
        "file",
        mimeType,
        Unit
    )

    suspend fun uploadImage(
        data: ByteArray,
        mimeType: ContentType,
        request: UploadImageRequest
    ) = uploadGeneric<UploadImageRequest, UploadFileResponse>(
        data,
        "image",
        mimeType,
        request
    )

    private suspend inline fun <reified T, reified R> uploadGeneric(
        data: ByteArray,
        type: String,
        mimeType: ContentType,
        request: T
    ): R {
        val parts = formData {
            append("attributes", Json.encodeToString(request))

            append(
                "file",
                data,
                Headers.build {
                    append(HttpHeaders.ContentType, mimeType.toString())
                }
            )
        }

        val response = http.submitFormWithBinaryData<HttpResponse>("${baseUrl}/api/$apiVersion/$type", formData = parts) {
            this.method = HttpMethod.Post

            header("Authorization", token)
        }

        return Json.decodeFromString<R>(response.readText())
    }

    // ===[ LINKS ]===
    suspend fun createImageLink(
        request: CreateImageLinkRequest
    ): CreateImageLinkResponse {
        val response = http.put<HttpResponse>("${baseUrl}/api/$apiVersion/images/links") {
            this.body = Json.encodeToString(request)

            header("Authorization", token)
        }

        return Json.decodeFromString(response.readText())
    }

    suspend fun createFileLink(
        request: CreateFileLinkRequest
    ): CreateImageLinkResponse {
        val response = http.put<HttpResponse>("${baseUrl}/api/$apiVersion/files/links") {
            this.body = Json.encodeToString(request)

            header("Authorization", token)
        }

        return Json.decodeFromString(response.readText())
    }

    suspend fun deleteImageLink(
        request: DeleteImageLinkRequest
    ) {
        http.delete<HttpResponse>("${baseUrl}/api/$apiVersion/images/links") {
            this.body = Json.encodeToString(request)

            header("Authorization", token)
        }
    }

    suspend fun deleteFileLink(
        request: DeleteFileLinkRequest
    ) {
        http.delete<HttpResponse>("${baseUrl}/api/$apiVersion/images/links") {
            this.body = Json.encodeToString(request)

            header("Authorization", token)
        }
    }

    // ===[ IMAGE CROPS ]===
    suspend fun addAllowedImageCrops(
        fileId: Long,
        request: AllowedImageCropsListRequest
    ) {
        http.put<HttpResponse>("${baseUrl}/api/$apiVersion/files/${fileId}/allowed-crops") {
            this.body = Json.encodeToString(request)

            header("Authorization", token)
        }
    }
}