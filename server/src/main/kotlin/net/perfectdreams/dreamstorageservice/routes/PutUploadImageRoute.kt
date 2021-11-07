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
import net.perfectdreams.dreamstorageservice.data.UploadImageRequest
import net.perfectdreams.dreamstorageservice.entities.AuthorizationToken
import net.perfectdreams.dreamstorageservice.entities.FileLink
import net.perfectdreams.dreamstorageservice.entities.StoredFile
import net.perfectdreams.dreamstorageservice.tables.FileLinks
import net.perfectdreams.dreamstorageservice.tables.StoredFiles
import net.perfectdreams.dreamstorageservice.utils.UploadedAsFileType
import org.apache.commons.codec.binary.Hex
import org.apache.commons.codec.digest.MessageDigestAlgorithms.SHA_256
import org.jetbrains.exposed.dao.DaoEntityID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.security.MessageDigest
import java.time.Instant

class PutUploadImageRoute(m: DreamStorageService) : RequiresAPIAuthenticationRoute(m, "/api/v1/upload/image") {
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

            val attributes = Json.decodeFromString<UploadImageRequest>(attributesPart.value)
            val unformattedPath = attributes.path

            val fileToBeStored = filePart.streamProvider.invoke().readAllBytes()
            val contentType = filePart.contentType ?: error("Missing Content-Type!")

            if (contentType.contentType != "image")
                error("Trying to upload a non-image file via the upload image endpoint!")

            var trueContentsToBeStored = fileToBeStored
            if (!attributes.skipOptimizations)
                trueContentsToBeStored = m.optimizeImage(contentType, trueContentsToBeStored)

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
                UploadedAsFileType.IMAGE,
                contentType,
                trueContentsToBeStored
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