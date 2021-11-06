package net.perfectdreams.dreamstorageservice.data

import kotlinx.serialization.Serializable

@Serializable
data class DeleteAllowedImageCropRequest(
    val cropX: Int,
    val cropY: Int,
    val cropWidth: Int,
    val cropHeight: Int
)