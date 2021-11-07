package net.perfectdreams.dreamstorageservice.data

import kotlinx.serialization.Serializable

@Serializable
data class Crop(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)