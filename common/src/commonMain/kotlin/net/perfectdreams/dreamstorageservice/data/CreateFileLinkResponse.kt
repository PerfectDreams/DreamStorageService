package net.perfectdreams.dreamstorageservice.data

import kotlinx.serialization.Serializable

@Serializable
data class CreateFileLinkResponse(
    val links: List<FileLink>
) {
    @Serializable
    data class FileLink(
        val id: Long,
        val linkInfo: LinkInfo
    )
}