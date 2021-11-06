package net.perfectdreams.dreamstorageservice.client.services

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.perfectdreams.dreamstorageservice.client.DreamStorageServiceClient
import net.perfectdreams.dreamstorageservice.data.DeleteFileLinkRequest
import net.perfectdreams.dreamstorageservice.data.GetNamespaceResponse
import net.perfectdreams.dreamstorageservice.data.UploadFileRequest
import net.perfectdreams.dreamstorageservice.data.UploadFileResponse

class FileLinkService(client: DreamStorageServiceClient) : Service(client) {
    suspend fun uploadFile(
        data: ByteArray,
        mimeType: ContentType,
        request: UploadFileRequest
    ): UploadFileResponse {
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

        val response = client.http.submitFormWithBinaryData<HttpResponse>("${client.baseUrl}/api/$apiVersion/upload", formData = parts) {
            this.method = HttpMethod.Put

            header("Authorization", client.token)
        }

        return Json.decodeFromString(response.readText())
    }

    suspend fun deleteLink(
        request: DeleteFileLinkRequest
    ) {
        client.http.delete<HttpResponse>("${client.baseUrl}/api/$apiVersion/delete") {
            this.body = Json.encodeToString(request)

            header("Authorization", client.token)
        }
    }
}