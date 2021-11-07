package net.perfectdreams.dreamstorageservice.data

import kotlinx.serialization.Serializable

@Serializable
data class DeleteImageLinkRequest(
    val links: List<LinkInfo>
)