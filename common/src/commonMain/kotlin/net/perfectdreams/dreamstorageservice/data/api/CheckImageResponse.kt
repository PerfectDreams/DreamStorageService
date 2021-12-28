package net.perfectdreams.dreamstorageservice.data.api

import kotlinx.serialization.Serializable

@Serializable
sealed class CheckImageResponse

@Serializable
data class ImageExistsResponse(
    val imageId: Long,
    val shaHash: String,
    val originalShaHash: String
) : CheckImageResponse()

@Serializable
class ImageDoesNotExistResponse : CheckImageResponse()