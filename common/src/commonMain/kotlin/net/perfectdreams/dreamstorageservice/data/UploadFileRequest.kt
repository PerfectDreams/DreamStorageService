package net.perfectdreams.dreamstorageservice.data

import kotlinx.serialization.Serializable

@Serializable
data class UploadFileRequest(
    val path: String,
    val skipOptimizations: Boolean
)