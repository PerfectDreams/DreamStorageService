package net.perfectdreams.dreamstorageservice.data.api

import kotlinx.serialization.Serializable

@Serializable
data class CreateFileLinkRequest(
    val fileId: Long,
    val folder: String,
    val file: String
)