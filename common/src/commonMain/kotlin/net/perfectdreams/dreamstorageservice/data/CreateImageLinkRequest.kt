package net.perfectdreams.dreamstorageservice.data

import kotlinx.serialization.Serializable

@Serializable
data class CreateImageLinkRequest(
    val imageId: Long,
    val folder: String,
    val file: String
)