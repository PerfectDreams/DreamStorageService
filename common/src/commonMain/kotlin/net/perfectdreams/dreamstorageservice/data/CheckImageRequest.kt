package net.perfectdreams.dreamstorageservice.data

import kotlinx.serialization.Serializable

@Serializable
data class CheckImageRequest(
    val skipOptimizations: Boolean
)