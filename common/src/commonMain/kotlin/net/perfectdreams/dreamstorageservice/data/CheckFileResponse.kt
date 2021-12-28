package net.perfectdreams.dreamstorageservice.data

import kotlinx.serialization.Serializable

sealed class CheckFileResponse

@Serializable
data class FileExistsResponse(
    val fileId: Long,
    val shaHash: String
) : CheckFileResponse()

@Serializable
class FileDoesNotExistResponse : CheckFileResponse()