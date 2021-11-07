package net.perfectdreams.dreamstorageservice.data

import kotlinx.serialization.Serializable

@Serializable
data class CreateFileLinkRequest(
    val fileId: Long,
    val links: List<LinkInfo>
)