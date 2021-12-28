package net.perfectdreams.dreamstorageservice.data

import kotlinx.serialization.Serializable

@Serializable
data class FileLinkInfo(
    val id: Long,
    val fileId: Long,
    val folder: String,
    val file: String
)