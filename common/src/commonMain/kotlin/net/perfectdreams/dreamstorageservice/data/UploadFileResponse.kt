package net.perfectdreams.dreamstorageservice.data

import kotlinx.serialization.Serializable

@Serializable
data class UploadFileResponse(
    val fileId: Long
)