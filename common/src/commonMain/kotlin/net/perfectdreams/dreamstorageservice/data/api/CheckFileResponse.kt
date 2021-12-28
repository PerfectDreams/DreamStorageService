package net.perfectdreams.dreamstorageservice.data.api

import kotlinx.serialization.Serializable

@Serializable
sealed class CheckFileResponse

@Serializable
data class FileExistsResponse(
    val fileId: Long,
    val shaHash: String
) : CheckFileResponse()

@Serializable
class FileDoesNotExistResponse : CheckFileResponse()