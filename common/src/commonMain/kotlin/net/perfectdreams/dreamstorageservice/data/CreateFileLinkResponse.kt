package net.perfectdreams.dreamstorageservice.data

import kotlinx.serialization.Serializable

@Serializable
data class CreateFileLinkResponse(
    val id: Long,
    val folder: String,
    val file: String
)