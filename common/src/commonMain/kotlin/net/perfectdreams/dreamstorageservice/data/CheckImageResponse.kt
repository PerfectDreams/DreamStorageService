package net.perfectdreams.dreamstorageservice.data

import kotlinx.serialization.Serializable

sealed class CheckImageResponse

@Serializable
data class ImageExistsResponse(
    val imageId: Long,
    val shaHash: String,
    val originalShaHash: String
) : CheckImageResponse()

@Serializable
class ImageDoesNotExistResponse : CheckImageResponse()