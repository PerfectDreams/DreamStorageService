package net.perfectdreams.dreamstorageservice.data

import kotlinx.serialization.Serializable

@Serializable
data class DeleteFileLinkRequest(
    val path: String
)