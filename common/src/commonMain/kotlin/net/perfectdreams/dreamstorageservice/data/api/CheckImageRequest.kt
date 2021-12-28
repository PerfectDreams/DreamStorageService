package net.perfectdreams.dreamstorageservice.data.api

import kotlinx.serialization.Serializable

@Serializable
data class CheckImageRequest(
    val skipOptimizations: Boolean
)