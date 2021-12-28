package net.perfectdreams.dreamstorageservice.data.api

import kotlinx.serialization.Serializable
import net.perfectdreams.dreamstorageservice.data.ImageInfo

@Serializable
data class UploadImageResponse(
    val isUnique: Boolean,
    val info: ImageInfo
)