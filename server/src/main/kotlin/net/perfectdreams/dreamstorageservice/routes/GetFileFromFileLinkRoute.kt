package net.perfectdreams.dreamstorageservice.routes

import com.github.benmanes.caffeine.cache.Caffeine
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import net.perfectdreams.dreamstorageservice.DreamStorageService
import net.perfectdreams.dreamstorageservice.entities.FileLink
import net.perfectdreams.dreamstorageservice.entities.ManipulatedStoredFile
import net.perfectdreams.dreamstorageservice.tables.AllowedImageCrops
import net.perfectdreams.dreamstorageservice.tables.ManipulatedStoredFiles
import net.perfectdreams.sequins.ktor.BaseRoute
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

class GetFileFromFileLinkRoute(val m: DreamStorageService) : BaseRoute("/{path...}") {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    // Used to avoid manipulating the same image at the same time
    private val manipulationMutexes = Caffeine.newBuilder()
        .expireAfterAccess(1L, TimeUnit.MINUTES)
        .build<String, Mutex>()
        .asMap()

    override suspend fun onRequest(call: ApplicationCall) {
        val path = call.parameters.getAll("path")!!
        val joinedPath = path.joinToString("/")

        // Remove the extension
        val joinedPathWithoutExtension = joinedPath.substringBeforeLast(".")
        val storedFile = m.transaction {
            FileLink.findById(joinedPathWithoutExtension)?.storedFile
        }

        if (storedFile == null) {
            logger.info { "User requested file in link $joinedPath, but the file doesn't exist!" }
            call.respondText("", status = HttpStatusCode.NotFound)
        } else {
            val fileExtension = joinedPath.substringAfterLast(".")

            // We will get the file source mime type...
            val mimeType = ContentType.parse(storedFile.mimeType)
            val mimeTypeBasedOnTheExtension = when (fileExtension) {
                "png" -> ContentType.Image.PNG
                "jpg", "jpeg" -> ContentType.Image.JPEG
                else -> { // If all else fails, default to the file's mime type
                    // In this case, we will only accept requests that match the original file extension
                    if (storedFile.originalExtension != fileExtension) { // Doesn't match, bye!
                        call.respondText("", status = HttpStatusCode.NotFound)
                        return
                    }

                    mimeType
                }
            }

            val isImage = mimeType.contentType == "image"

            val cropX = call.request.queryParameters["crop_x"]?.toIntOrNull()
            val cropY = call.request.queryParameters["crop_y"]?.toIntOrNull()
            val cropWidth = call.request.queryParameters["crop_width"]?.toIntOrNull()
            val cropHeight = call.request.queryParameters["crop_height"]?.toIntOrNull()
            val size = call.request.queryParameters["size"]?.toIntOrNull()

            logger.info { "User requested file in link $joinedPath, cropX = $cropX; cropY = $cropY; cropWidth = $cropWidth; cropHeight = $cropHeight; size = $size" }

            val requiresManipulation = (cropX != null && cropY != null && cropWidth != null && cropHeight != null) || size != null || mimeType != mimeTypeBasedOnTheExtension

            if (requiresManipulation) {
                // Can't crop/change size of something that isn't an image, right? lol
                if (!isImage) {
                    call.respondText("", status = HttpStatusCode.BadRequest)
                    return
                }

                val mutex = manipulationMutexes.getOrPut(joinedPath) { Mutex() }
                mutex.withLock {
                    val cachedManipulation = m.transaction {
                        ManipulatedStoredFile.find {
                            ManipulatedStoredFiles.storedFile eq storedFile.id and
                                    (ManipulatedStoredFiles.mimeType eq mimeTypeBasedOnTheExtension.toString()) and
                                    (ManipulatedStoredFiles.cropX eq cropX) and
                                    (ManipulatedStoredFiles.cropY eq cropY) and
                                    (ManipulatedStoredFiles.cropWidth eq cropWidth) and
                                    (ManipulatedStoredFiles.cropHeight eq cropHeight) and
                                    (ManipulatedStoredFiles.size eq size)
                        }.firstOrNull()
                    }

                    if (cachedManipulation != null) {
                        logger.info { "User requested file in link $joinedPath, cropX = $cropX; cropY = $cropY; cropWidth = $cropWidth; cropHeight = $cropHeight; size = $size; using cached manipulation" }
                        call.respondBytes(cachedManipulation.data, mimeType)
                    } else {
                        // Supported Manipulation Sources
                        if (mimeType != ContentType.Image.JPEG && mimeType != ContentType.Image.PNG) {
                            call.respondText("", status = HttpStatusCode.BadRequest)
                            return
                        }

                        // Supported Manipulation Targets
                        if (mimeTypeBasedOnTheExtension != ContentType.Image.JPEG && mimeTypeBasedOnTheExtension != ContentType.Image.PNG) {
                            call.respondText("", status = HttpStatusCode.BadRequest)
                            return
                        }

                        logger.info { "User requested file in link $joinedPath, cropX = $cropX; cropY = $cropY; cropWidth = $cropWidth; cropHeight = $cropHeight; size = $size; creating manipulated image from scratch" }
                        var manipulatedImage: BufferedImage? = null

                        if (cropX != null && cropY != null && cropWidth != null && cropHeight != null) {
                            // Check if this crop request is allowed
                            // (To avoid malicious users creating a lot of crop requests)
                            val isCropAllowed = m.transaction {
                                AllowedImageCrops.select {
                                    AllowedImageCrops.storedFile eq storedFile.id and
                                            (AllowedImageCrops.cropX eq cropX) and
                                            (AllowedImageCrops.cropY eq cropY) and
                                            (AllowedImageCrops.cropWidth eq cropWidth) and
                                            (AllowedImageCrops.cropHeight eq cropHeight)
                                }.count() != 0L
                            }

                            if (!isCropAllowed) {
                                call.respondText("", status = HttpStatusCode.Unauthorized)
                                return
                            }

                            val image = manipulatedImage ?: withContext(Dispatchers.IO) { ImageIO.read(storedFile.data.inputStream()) }
                            manipulatedImage = image.getSubimage(cropX, cropY, cropWidth, cropHeight)
                        }

                        if (size != null) {
                            if (mimeType != ContentType.Image.JPEG && mimeType != ContentType.Image.PNG) {
                                call.respondText("", status = HttpStatusCode.BadRequest)
                                return
                            }

                            if (size == 16 || size == 32 || size == 64 || size == 128 || size == 256 || size == 512) {
                                val image = manipulatedImage ?: withContext(Dispatchers.IO) { ImageIO.read(storedFile.data.inputStream()) }
                                val targetWidth: Int
                                val targetHeight: Int

                                if (image.height > image.width) {
                                    targetHeight = size
                                    targetWidth = (size * image.width) / image.height
                                } else {
                                    targetWidth = size
                                    targetHeight = (size * image.height) / image.width
                                }

                                manipulatedImage = toBufferedImage(image.getScaledInstance(targetWidth, targetHeight, BufferedImage.SCALE_SMOOTH))
                            } else {
                                call.respondText("", status = HttpStatusCode.BadRequest)
                                return
                            }
                        }

                        val baos = ByteArrayOutputStream()
                        // We read the image if it is null because maybe we just want to convert it to another file type
                        ImageIO.write(
                            withContext(Dispatchers.IO) { ImageIO.read(storedFile.data.inputStream()) } ?: manipulatedImage,
                            if (mimeTypeBasedOnTheExtension == ContentType.Image.JPEG) "jpg" else "png",
                            baos
                        )
                        val byteArrayData = baos.toByteArray()

                        // Optimize image
                        var trueContentsToBeSent = byteArrayData
                        if (mimeType.contentType == "image")
                            trueContentsToBeSent = m.optimizeImage(mimeTypeBasedOnTheExtension, trueContentsToBeSent)

                        // Store the manipulated file in the database!
                        m.transaction {
                            ManipulatedStoredFile.new {
                                // I don't know why it needs to be like this, this is a horrible hack
                                // If we set the ID in the ".new(idhere) {}" call, it fails with a weird "created_at" is missing error
                                // See: https://github.com/JetBrains/Exposed/issues/1379
                                this.mimeType = mimeTypeBasedOnTheExtension.toString()
                                this.cropX = cropX
                                this.cropY = cropY
                                this.cropWidth = cropWidth
                                this.cropHeight = cropHeight
                                this.size = size
                                this.createdAt = Instant.now()
                                this.storedFile = storedFile
                                this.data = trueContentsToBeSent
                            }
                        }

                        call.respondBytes(trueContentsToBeSent, mimeType)
                    }
                }
            } else {
                logger.info { "User requested file in link $joinedPath, no manipulation necessary" }
                call.respondBytes(storedFile.data, mimeType)
            }
        }
    }

    /**
     * Converts a given Image into a BufferedImage
     *
     * @param img The Image to be converted
     * @return The converted BufferedImage
     */
    fun toBufferedImage(img: Image): BufferedImage {
        if (img is BufferedImage) {
            return img
        }

        // Create a buffered image with transparency
        val bimage = BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB)

        // Draw the image on to the buffered image
        val bGr = bimage.createGraphics()
        bGr.drawImage(img, 0, 0, null)
        bGr.dispose()

        // Return the buffered image
        return bimage
    }
}