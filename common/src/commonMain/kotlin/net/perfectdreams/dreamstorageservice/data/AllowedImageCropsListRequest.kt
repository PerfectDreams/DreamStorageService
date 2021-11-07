package net.perfectdreams.dreamstorageservice.data

import kotlinx.serialization.Serializable

@Serializable
data class AllowedImageCropsListRequest(
    val crops: List<Crop>
)