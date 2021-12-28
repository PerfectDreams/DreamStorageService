package net.perfectdreams.dreamstorageservice.data.api

import kotlinx.serialization.Serializable
import net.perfectdreams.dreamstorageservice.data.Crop

@Serializable
data class AllowedImageCropsListRequest(
    val crops: List<Crop>
)