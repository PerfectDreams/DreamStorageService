package net.perfectdreams.dreamstorageservice.data.api

import kotlinx.serialization.Serializable
import net.perfectdreams.dreamstorageservice.data.ImageLinkInfo

@Serializable
data class CreateImageLinkResponse(
    val link: ImageLinkInfo
)