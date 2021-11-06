package net.perfectdreams.dreamstorageservice.data

import kotlinx.serialization.Serializable

@Serializable
data class AddAllowedImageCropRequest(
    val fileId: Long,
    val cropX: Int,
    val cropY: Int,
    val cropWidth: Int,
    val cropHeight: Int
)