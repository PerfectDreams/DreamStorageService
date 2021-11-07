package net.perfectdreams.dreamstorageservice.data

import kotlinx.serialization.Serializable

@Serializable
data class UploadImageRequest(
    val skipOptimizations: Boolean
)