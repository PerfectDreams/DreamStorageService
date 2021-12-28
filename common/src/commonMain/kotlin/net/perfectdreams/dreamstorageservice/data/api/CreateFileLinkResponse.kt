package net.perfectdreams.dreamstorageservice.data.api

import kotlinx.serialization.Serializable
import net.perfectdreams.dreamstorageservice.data.FileLinkInfo

@Serializable
data class CreateFileLinkResponse(
    val link: FileLinkInfo
)