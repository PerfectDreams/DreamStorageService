package net.perfectdreams.dreamstorageservice.data

import kotlinx.serialization.Serializable

@Serializable
data class CreateImageLinkResponse(
    val links: List<ImageLink>
) {
    @Serializable
    data class ImageLink(
        val id: Long,
        val linkInfo: LinkInfo
    )
}