package net.perfectdreams.dreamstorageservice.routes

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import net.perfectdreams.dreamstorageservice.DreamStorageService
import net.perfectdreams.dreamstorageservice.data.UploadFileRequest
import net.perfectdreams.dreamstorageservice.data.UploadFileResponse
import net.perfectdreams.dreamstorageservice.entities.AuthorizationToken
import net.perfectdreams.dreamstorageservice.utils.UploadedAsFileType
import org.apache.commons.codec.binary.Hex

class PutUploadFileRoute(m: DreamStorageService) : RequiresAPIAuthenticationRoute(m, "/api/v1/upload/file") {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override suspend fun onAuthenticatedRequest(call: ApplicationCall, token: AuthorizationToken) {
        withContext(Dispatchers.IO) {
            // Receive the uploaded file
            val multipart = call.receiveMultipart()
            val parts = multipart.readAllParts()
            val filePart = parts.first { it.name == "file" } as PartData.FileItem
            val attributesPart = parts.first { it.name == "attributes" } as PartData.FormItem

            val attributes = Json.decodeFromString<UploadFileRequest>(attributesPart.value)
            val unformattedPath = attributes.path

            val fileToBeStored = filePart.streamProvider.invoke().readAllBytes()
            val contentType = filePart.contentType ?: error("Missing Content-Type!")

            // Calculate the checksum
            val checksum = m.fileUtils.calculateChecksum(fileToBeStored)

            // Allows the user to format the upload path with a SHA-256 hash, neat!
            val originalExtension = unformattedPath.substringAfter(".")
            val pathWithoutNamespaceAndExtension = unformattedPath.format(Hex.encodeHexString(checksum)).substringBeforeLast(".")
            val path = token.namespace + "/" + pathWithoutNamespaceAndExtension

            val (storedFile, fileLink) = m.fileUtils.uploadFileAndCreateFileLink(
                token,
                path,
                originalExtension,
                checksum,
                UploadedAsFileType.FILE,
                contentType,
                fileToBeStored
            )

            logger.info { "Uploaded file ${storedFile.id.value} ${fileLink.path}" }

            call.respondText(
                Json.encodeToString(
                    UploadFileResponse(
                        storedFile.id.value,
                        "$pathWithoutNamespaceAndExtension.$originalExtension",
                        "$path.$originalExtension"
                    )
                )
            )
        }
    }
}