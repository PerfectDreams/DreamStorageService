package net.perfectdreams.dreamstorageservice.data

import kotlinx.serialization.Serializable

@Serializable
data class FileInfo(
    val fileId: Long,
    val shaHash: String
)