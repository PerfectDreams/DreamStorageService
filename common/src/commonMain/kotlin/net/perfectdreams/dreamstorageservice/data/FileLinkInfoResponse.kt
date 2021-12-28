package net.perfectdreams.dreamstorageservice.data

import kotlinx.serialization.Serializable

@Serializable
data class FileLinkInfoResponse(
    val id: Long,
    val fileId: Long,
    val folder: String,
    val file: String
)