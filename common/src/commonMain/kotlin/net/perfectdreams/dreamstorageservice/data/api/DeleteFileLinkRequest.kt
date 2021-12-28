package net.perfectdreams.dreamstorageservice.data.api

import kotlinx.serialization.Serializable

@Serializable
data class DeleteFileLinkRequest(
    val folder: String,
    val file: String
)