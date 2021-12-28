package net.perfectdreams.dreamstorageservice.data

import kotlinx.serialization.Serializable

@Serializable
data class ImageInfo(
    val imageId: Long,
    val shaHash: String,
    val originalShaHash: String
)