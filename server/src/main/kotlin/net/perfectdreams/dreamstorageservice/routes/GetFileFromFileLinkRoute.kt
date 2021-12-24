package net.perfectdreams.dreamstorageservice.routes

import com.github.benmanes.caffeine.cache.Caffeine
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import net.perfectdreams.dreamstorageservice.DreamStorageService
import net.perfectdreams.dreamstorageservice.entities.FileLink
import net.perfectdreams.dreamstorageservice.entities.ImageLink
import net.perfectdreams.dreamstorageservice.entities.ManipulatedStoredImage
import net.perfectdreams.dreamstorageservice.tables.AllowedImageCrops
import net.perfectdreams.dreamstorageservice.tables.FileLinks
import net.perfectdreams.dreamstorageservice.tables.ImageLinks
import net.perfectdreams.dreamstorageservice.tables.ManipulatedStoredImages
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

        private const val PREFERRED_PNG_TYPE = BufferedImage.TYPE_INT_ARGB
        private const val PREFERRED_JPEG_TYPE = BufferedImage.TYPE_INT_RGB
    }

    // Used to avoid manipulating the same image at the same time
    private val manipulationMutexes = Caffeine.newBuilder()
        .expireAfterAccess(1L, TimeUnit.MINUTES)
        .build<String, Mutex>()
        .asMap()

    override suspend fun onRequest(call: ApplicationCall) {
        val path = call.parameters.getAll("path")!!
        val joinedPath = path.joinToString("/")
        val joinedFolderWithoutNamespace = path.drop(1)
            .dropLast(1)
            .joinToString("/")

        // If it is empty, just return a error 404 not found
        if (joinedFolderWithoutNamespace.isEmpty()) {
            call.respondText("", status = HttpStatusCode.NotFound)
            return
        }

        val file = path.last()
        val fileWithoutExtension = file.substringBeforeLast(".")

        // I don't think this can happen, buuuuut...
        val namespace = path.firstOrNull() ?: return

        val authToken = m.retrieveAuthenticationTokenFromNamespace(namespace) ?: return // There isn't a auth token matching that namespace!

        // Remove the extension
        val joinedPathWithoutExtension = joinedPath.substringBeforeLast(".")

        // Check for images
        val storedImage = m.transaction {
            ImageLink.find {
                ImageLinks.createdBy eq authToken.id and (ImageLinks.folder eq joinedFolderWithoutNamespace and (ImageLinks.file eq fileWithoutExtension))
            }.firstOrNull()?.storedImage
        }

        if (storedImage == null) {
            // Check for files
            val storedFile = m.transaction {
                FileLink.find {
                    FileLinks.createdBy eq authToken.id and (FileLinks.folder eq joinedFolderWithoutNamespace and (FileLinks.file eq file))
                }.firstOrNull()?.storedFile
            }

            if (storedFile != null) {
                logger.info { "User requested file in link $joinedPath" }
                call.respondBytes(storedFile.data, ContentType.parse(storedFile.mimeType))
                return
            }

            logger.info { "User requested image or file in link $joinedPath, but the image and the file doesn't exist!" }
            call.respondText("", status = HttpStatusCode.NotFound)
        } else {
            val fileExtension = joinedPath.substringAfterLast(".")

            // We will get the file source mime type...
            val mimeType = ContentType.parse(storedImage.mimeType)

            val mimeTypeBasedOnTheExtension = when (fileExtension) {
                "png" -> ContentType.Image.PNG
                "jpg", "jpeg" -> ContentType.Image.JPEG
                "gif" -> ContentType.Image.GIF
                else -> { // Unsupported for now
                    call.respondText("", status = HttpStatusCode.NotFound)
                    return
                }
            }

            val cropX = call.request.queryParameters["crop_x"]?.toIntOrNull()
            val cropY = call.request.queryParameters["crop_y"]?.toIntOrNull()
            val cropWidth = call.request.queryParameters["crop_width"]?.toIntOrNull()
            val cropHeight = call.request.queryParameters["crop_height"]?.toIntOrNull()
            val size = call.request.queryParameters["size"]?.toIntOrNull()

            logger.info { "User requested image in link $joinedPath, cropX = $cropX; cropY = $cropY; cropWidth = $cropWidth; cropHeight = $cropHeight; size = $size" }

            val requiresManipulation = (cropX != null && cropY != null && cropWidth != null && cropHeight != null) || size != null || mimeType != mimeTypeBasedOnTheExtension

            if (requiresManipulation) {
                val mutex = manipulationMutexes.getOrPut(joinedPath + "?${call.request.queryString()}") { Mutex() }
                mutex.withLock {
                    val cachedManipulation = m.transaction {
                        ManipulatedStoredImage.find {
                            ManipulatedStoredImages.storedImage eq storedImage.id and
                                    (ManipulatedStoredImages.mimeType eq mimeTypeBasedOnTheExtension.toString()) and
                                    (ManipulatedStoredImages.cropX eq cropX) and
                                    (ManipulatedStoredImages.cropY eq cropY) and
                                    (ManipulatedStoredImages.cropWidth eq cropWidth) and
                                    (ManipulatedStoredImages.cropHeight eq cropHeight) and
                                    (ManipulatedStoredImages.size eq size)
                        }.firstOrNull()
                    }

                    if (cachedManipulation != null) {
                        logger.info { "User requested image in link $joinedPath, cropX = $cropX; cropY = $cropY; cropWidth = $cropWidth; cropHeight = $cropHeight; size = $size; using cached manipulation" }
                        call.respondBytes(cachedManipulation.data, mimeType)
                    } else {
                        // Supported Manipulation Targets
                        // Currently GIFs isn't a supported manipulation target!
                        val preferredImageType = when (mimeTypeBasedOnTheExtension) {
                            ContentType.Image.PNG -> PREFERRED_PNG_TYPE
                            ContentType.Image.JPEG -> PREFERRED_JPEG_TYPE
                            else -> {
                                call.respondText("", status = HttpStatusCode.BadRequest)
                                return
                            }
                        }

                        logger.info { "User requested image in link $joinedPath, cropX = $cropX; cropY = $cropY; cropWidth = $cropWidth; cropHeight = $cropHeight; size = $size; creating manipulated image from scratch" }
                        var manipulatedImage: BufferedImage? = null

                        if (cropX != null && cropY != null && cropWidth != null && cropHeight != null) {
                            // Check if this crop request is allowed
                            // (To avoid malicious users creating a lot of crop requests)
                            val isCropAllowed = m.transaction {
                                AllowedImageCrops.select {
                                    AllowedImageCrops.storedImage eq storedImage.id and
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

                            val image = manipulatedImage ?: withContext(Dispatchers.IO) { ImageIO.read(storedImage.data.inputStream()) }
                            manipulatedImage = image.getSubimage(cropX, cropY, cropWidth, cropHeight)
                        }

                        if (size != null) {
                            if (size == 16 || size == 32 || size == 64 || size == 128 || size == 256 || size == 512) {
                                val image = manipulatedImage ?: withContext(Dispatchers.IO) { ImageIO.read(storedImage.data.inputStream()) }
                                val targetWidth: Int
                                val targetHeight: Int

                                if (image.height > image.width) {
                                    targetHeight = size
                                    targetWidth = (size * image.width) / image.height
                                } else {
                                    targetWidth = size
                                    targetHeight = (size * image.height) / image.width
                                }

                                manipulatedImage = toBufferedImage(
                                    image.getScaledInstance(targetWidth, targetHeight, BufferedImage.SCALE_SMOOTH),
                                    preferredImageType
                                )
                            } else {
                                call.respondText("", status = HttpStatusCode.BadRequest)
                                return
                            }
                        }

                        // We read the image if it is null because maybe we just want to convert it to another file type
                        if (manipulatedImage == null)
                            manipulatedImage = withContext(Dispatchers.IO) { ImageIO.read(storedImage.data.inputStream()) }

                        requireNotNull(manipulatedImage) { "Manipulated image is null!" } // This should never be null at this point, but hey, who knows

                        // This will convert the image to the preferred content type
                        // This is useful for JPEG images because if the image has alpha (TYPE_INT_ARGB), the result file will have 0 bytes
                        // https://stackoverflow.com/a/66954103/7271796
                        if (manipulatedImage.type != preferredImageType) {
                            val newBufferedImage = BufferedImage(
                                manipulatedImage.width,
                                manipulatedImage.height,
                                preferredImageType
                            )
                            newBufferedImage.graphics.drawImage(manipulatedImage, 0, 0, null)
                            manipulatedImage = newBufferedImage
                        }

                        val baos = ByteArrayOutputStream()
                        ImageIO.write(
                            manipulatedImage,
                            if (mimeTypeBasedOnTheExtension == ContentType.Image.JPEG) "jpg" else "png",
                            baos
                        )
                        val byteArrayData = baos.toByteArray()

                        // Optimize image
                        val trueContentsToBeSent = m.optimizeImage(mimeTypeBasedOnTheExtension, byteArrayData)

                        // Store the manipulated file in the database!
                        m.transaction {
                            ManipulatedStoredImage.new {
                                this.mimeType = mimeTypeBasedOnTheExtension.toString()
                                this.cropX = cropX
                                this.cropY = cropY
                                this.cropWidth = cropWidth
                                this.cropHeight = cropHeight
                                this.size = size
                                this.createdAt = Instant.now()
                                this.storedImage = storedImage
                                this.data = trueContentsToBeSent
                            }
                        }

                        call.respondBytes(trueContentsToBeSent, mimeType)
                    }
                }
            } else {
                logger.info { "User requested image in link $joinedPath, no manipulation necessary" }
                call.respondBytes(storedImage.data, mimeType)
            }
        }
    }

    /**
     * Converts a given Image into a BufferedImage
     *
     * @param img The Image to be converted
     * @return The converted BufferedImage
     */
    fun toBufferedImage(img: Image, imageType: Int): BufferedImage {
        if (img is BufferedImage) {
            return img
        }

        // Create a buffered image with transparency
        val bimage = BufferedImage(img.getWidth(null), img.getHeight(null), imageType)

        // Draw the image on to the buffered image
        val bGr = bimage.createGraphics()
        bGr.drawImage(img, 0, 0, null)
        bGr.dispose()

        // Return the buffered image
        return bimage
    }
}