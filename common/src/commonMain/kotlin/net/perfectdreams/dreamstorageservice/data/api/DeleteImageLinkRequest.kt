package net.perfectdreams.dreamstorageservice.data.api

import kotlinx.serialization.Serializable

@Serializable
data class DeleteImageLinkRequest(
    val folder: String,
    val file: String
)