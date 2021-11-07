package net.perfectdreams.dreamstorageservice.data

import kotlinx.serialization.Serializable

@Serializable
data class CreateImageLinkResponse(
    val id: Long,
    val folder: String,
    val file: String
)