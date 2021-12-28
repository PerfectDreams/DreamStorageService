package net.perfectdreams.dreamstorageservice.data

import kotlinx.serialization.Serializable

@Serializable
data class ImageLinkInfoResponse(
    val id: Long,
    val imageId: Long,
    val folder: String,
    val file: String
)