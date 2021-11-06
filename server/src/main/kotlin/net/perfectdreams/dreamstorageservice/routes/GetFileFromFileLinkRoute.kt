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
import net.perfectdreams.dreamstorageservice.tables.AuthorizationTokens.token
import net.perfectdreams.dreamstorageservice.tables.FileLinks
import net.perfectdreams.dreamstorageservice.tables.ManipulatedStoredFiles
import net.perfectdreams.sequins.ktor.BaseRoute
import org.jetbrains.exposed.dao.DaoEntityID
import org.jetbrains.exposed.sql.and
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
        val storedFile = m.transaction {
            FileLink.findById(joinedPath)?.storedFile
        }

        if (storedFile == null) {
            logger.info { "User requested file in link $joinedPath, but the file doesn't exist!" }
            call.respondText("", status = HttpStatusCode.NotFound)
        } else {
            logger.info { "User requested file in link $joinedPath" }

            val mimeType = ContentType.parse(storedFile.mimeType)

            val cropX = call.parameters["crop_x"]?.toIntOrNull()
            val cropY = call.parameters["crop_y"]?.toIntOrNull()
            val cropWidth = call.parameters["crop_width"]?.toIntOrNull()
            val cropHeight = call.parameters["crop_height"]?.toIntOrNull()
            val scale = call.parameters["scale"]?.toIntOrNull()

            val requiresManipulation = (cropX != null && cropY != null && cropWidth != null && cropHeight != null) || scale != null

            if (requiresManipulation) {
                val mutex = manipulationMutexes.getOrPut(joinedPath) { Mutex() }
                mutex.withLock {
                    val cachedManipulation = m.transaction {
                        ManipulatedStoredFile.find {
                            ManipulatedStoredFiles.cropX eq cropX and
                                    (ManipulatedStoredFiles.cropY eq cropY) and
                                    (ManipulatedStoredFiles.cropWidth eq cropWidth) and
                                    (ManipulatedStoredFiles.cropHeight eq cropHeight) and
                                    (ManipulatedStoredFiles.scale eq scale)
                        }.firstOrNull()
                    }

                    if (cachedManipulation != null) {
                        call.respondBytes(cachedManipulation.data, mimeType)
                    } else {
                        var manipulatedImage: BufferedImage? = null

                        if (cropX != null && cropY != null && cropWidth != null && cropHeight != null) {
                            if (mimeType != ContentType.Image.JPEG && mimeType != ContentType.Image.PNG) {
                                call.respondText("", status = HttpStatusCode.BadRequest)
                                return
                            }

                            val image = manipulatedImage ?: withContext(Dispatchers.IO) { ImageIO.read(storedFile.data.inputStream()) }
                            manipulatedImage = image.getSubimage(cropX, cropY, cropWidth, cropHeight)
                        }

                        if (scale != null) {
                            if (mimeType != ContentType.Image.JPEG && mimeType != ContentType.Image.PNG) {
                                call.respondText("", status = HttpStatusCode.BadRequest)
                                return
                            }

                            if (scale == 16 || scale == 32 || scale == 64 || scale == 128 || scale == 256 || scale == 512) {
                                val image = withContext(Dispatchers.IO) { ImageIO.read(storedFile.data.inputStream()) }
                                val targetWidth: Int
                                val targetHeight: Int

                                if (image.height > image.width) {
                                    targetHeight = scale
                                    targetWidth = (scale * image.width) / image.height
                                } else {
                                    targetWidth = scale
                                    targetHeight = (scale * image.height) / image.width
                                }

                                manipulatedImage = toBufferedImage(image.getScaledInstance(targetWidth, targetHeight, BufferedImage.SCALE_SMOOTH))
                            } else {
                                call.respondText("", status = HttpStatusCode.BadRequest)
                                return
                            }
                        }

                        val baos = ByteArrayOutputStream()

                        // Store the manipulated file in the database!
                        m.transaction {
                            ManipulatedStoredFile.new {
                                // I don't know why it needs to be like this, this is a horrible hack
                                // If we set the ID in the ".new(idhere) {}" call, it fails with a weird "created_at" is missing error
                                // See: https://github.com/JetBrains/Exposed/issues/1379
                                this.mimeType = mimeType.toString()
                                this.cropX = cropX
                                this.cropY = cropY
                                this.cropWidth = cropWidth
                                this.cropHeight = cropHeight
                                this.scale = scale
                                this.createdAt = Instant.now()
                                this.storedFile = storedFile
                            }
                        }

                        ImageIO.write(manipulatedImage, if (mimeType == ContentType.Image.JPEG) "jpeg" else "png", baos)

                        call.respondBytes(baos.toByteArray(), mimeType)
                    }
                }
            } else {
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