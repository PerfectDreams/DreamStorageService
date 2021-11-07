package net.perfectdreams.dreamstorageservice.data

import kotlinx.serialization.Serializable

sealed class UploadRequest {
    abstract val path: String
}

@Serializable
data class UploadFileRequest(override val path: String) : UploadRequest()

@Serializable
data class UploadImageRequest(override val path: String, val skipOptimizations: Boolean) : UploadRequest()
