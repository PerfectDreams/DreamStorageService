package net.perfectdreams.dreamstorageservice.data.api

import kotlinx.serialization.Serializable
import net.perfectdreams.dreamstorageservice.data.FileInfo

@Serializable
data class UploadFileResponse(
    val isUnique: Boolean,
    val info: FileInfo
)