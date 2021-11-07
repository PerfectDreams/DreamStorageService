package net.perfectdreams.dreamstorageservice.data

import kotlinx.serialization.Serializable

@Serializable
data class LinkInfo(
    val folder: String,
    val file: String
)