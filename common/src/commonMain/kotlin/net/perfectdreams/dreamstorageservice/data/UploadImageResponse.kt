package net.perfectdreams.dreamstorageservice.data

import kotlinx.serialization.Serializable

@Serializable
data class UploadImageResponse(
    val imageId: Long,
    val isUnique: Boolean,
    val shaHash: String,
    val originalShaHash: String
)