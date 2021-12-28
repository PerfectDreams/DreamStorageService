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
import net.perfectdreams.dreamstorageservice.data.CheckFileResponse
import net.perfectdreams.dreamstorageservice.data.CheckImageRequest
import net.perfectdreams.dreamstorageservice.data.CheckImageResponse
import net.perfectdreams.dreamstorageservice.data.CreateFileLinkRequest
import net.perfectdreams.dreamstorageservice.data.CreateImageLinkRequest
import net.perfectdreams.dreamstorageservice.data.CreateImageLinkResponse
import net.perfectdreams.dreamstorageservice.data.DeleteFileLinkRequest
import net.perfectdreams.dreamstorageservice.data.DeleteImageLinkRequest
import net.perfectdreams.dreamstorageservice.data.FileLinkInfoResponse
import net.perfectdreams.dreamstorageservice.data.GetNamespaceResponse
import net.perfectdreams.dreamstorageservice.data.ImageLinkInfoResponse
import net.perfectdreams.dreamstorageservice.data.UploadFileResponse
import net.perfectdreams.dreamstorageservice.data.UploadImageRequest
import net.perfectdreams.dreamstorageservice.data.UploadImageResponse

class DreamStorageServiceClient(baseUrl: String, val token: String, val http: HttpClient) {
    companion object {
        private const val apiVersion = "v1"

        // To avoid the client crashing due to additional fields that aren't mapped, let's ignore unknown keys
        // This is useful if we want to add new information but we don't want older clients to crash
        private val json = Json {
            ignoreUnknownKeys = true
        }
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

        return json.decodeFromString<GetNamespaceResponse>(response.readText()).namespace
    }

    // ===[ FILE UPLOAD ]===
    suspend fun uploadFile(
        data: ByteArray,
        mimeType: ContentType
    ) = uploadGeneric<Unit, UploadFileResponse>(
        data,
        "files",
        mimeType,
        Unit
    )

    suspend fun uploadImage(
        data: ByteArray,
        mimeType: ContentType,
        request: UploadImageRequest
    ) = uploadGeneric<UploadImageRequest, UploadImageResponse>(
        data,
        "images",
        mimeType,
        request
    )

    suspend fun checkFile(
        data: ByteArray,
        mimeType: ContentType
    ) = uploadGeneric<Unit, CheckFileResponse>(
        data,
        "files/check",
        mimeType,
        Unit
    )

    suspend fun checkImage(
        data: ByteArray,
        mimeType: ContentType,
        request: CheckImageRequest
    ) = uploadGeneric<CheckImageRequest, CheckImageResponse>(
        data,
        "images/check",
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
            append("attributes", json.encodeToString(request))

            append(
                "file",
                data,
                Headers.build {
                    append(HttpHeaders.ContentType, mimeType.toString())
                    append(HttpHeaders.ContentDisposition, "filename=file") // This needs to be present for it to be recognized as a FileItem!
                }
            )
        }

        val response = http.submitFormWithBinaryData<HttpResponse>("${baseUrl}/api/$apiVersion/$type", formData = parts) {
            this.method = HttpMethod.Post

            header("Authorization", token)
        }

        return json.decodeFromString<R>(response.readText())
    }

    // ===[ LINKS ]===
    suspend fun createImageLink(
        request: CreateImageLinkRequest
    ): CreateImageLinkResponse {
        val response = http.put<HttpResponse>("${baseUrl}/api/$apiVersion/images/links") {
            this.body = json.encodeToString(request)

            header("Authorization", token)
        }

        return json.decodeFromString(response.readText())
    }

    suspend fun createFileLink(
        request: CreateFileLinkRequest
    ): CreateImageLinkResponse {
        val response = http.put<HttpResponse>("${baseUrl}/api/$apiVersion/files/links") {
            this.body = json.encodeToString(request)

            header("Authorization", token)
        }

        return json.decodeFromString(response.readText())
    }

    suspend fun getImageLinkInfo(
        path: String
    ): ImageLinkInfoResponse? {
        val response = http.put<HttpResponse>("${baseUrl}/api/$apiVersion/images/links/$path") {
            header("Authorization", token)
        }

        if (response.status == HttpStatusCode.NotFound)
            return null

        return json.decodeFromString(response.readText())
    }

    suspend fun getFileLinkInfo(
        path: String
    ): FileLinkInfoResponse? {
        val response = http.put<HttpResponse>("${baseUrl}/api/$apiVersion/files/links/$path") {
            header("Authorization", token)
        }

        if (response.status == HttpStatusCode.NotFound)
            return null

        return json.decodeFromString(response.readText())
    }

    suspend fun deleteImageLink(
        request: DeleteImageLinkRequest
    ) {
        http.delete<HttpResponse>("${baseUrl}/api/$apiVersion/images/links") {
            this.body = json.encodeToString(request)

            header("Authorization", token)
        }
    }

    suspend fun deleteFileLink(
        request: DeleteFileLinkRequest
    ) {
        http.delete<HttpResponse>("${baseUrl}/api/$apiVersion/files/links") {
            this.body = json.encodeToString(request)

            header("Authorization", token)
        }
    }

    // ===[ IMAGE CROPS ]===
    suspend fun addAllowedImageCrops(
        imageId: Long,
        request: AllowedImageCropsListRequest
    ) {
        http.put<HttpResponse>("${baseUrl}/api/$apiVersion/images/${imageId}/allowed-crops") {
            this.body = json.encodeToString(request)

            header("Authorization", token)
        }
    }
}