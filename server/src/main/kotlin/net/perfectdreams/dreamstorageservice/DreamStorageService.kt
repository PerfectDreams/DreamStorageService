package net.perfectdreams.dreamstorageservice

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.util.IsolationLevel
import io.ktor.server.application.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cachingheaders.*
import io.ktor.server.plugins.compression.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import net.perfectdreams.dreamstorageservice.entities.AuthorizationToken
import net.perfectdreams.dreamstorageservice.plugins.configureRouting
import net.perfectdreams.dreamstorageservice.routes.GetFileFromFileLinkRoute
import net.perfectdreams.dreamstorageservice.routes.api.GetNamespacev1Route
import net.perfectdreams.dreamstorageservice.routes.api.files.DeleteFileLinkRoute
import net.perfectdreams.dreamstorageservice.routes.api.GetNamespacev2Route
import net.perfectdreams.dreamstorageservice.routes.api.files.GetFileLinksInfoRoute
import net.perfectdreams.dreamstorageservice.routes.api.files.PostCheckFileRoute
import net.perfectdreams.dreamstorageservice.routes.api.files.PostUploadFileRoute
import net.perfectdreams.dreamstorageservice.routes.api.files.PutFileLinkRoute
import net.perfectdreams.dreamstorageservice.routes.api.images.DeleteImageLinkRoute
import net.perfectdreams.dreamstorageservice.routes.api.images.GetImageLinksInfoRoute
import net.perfectdreams.dreamstorageservice.routes.api.images.PostCheckImageRoute
import net.perfectdreams.dreamstorageservice.routes.api.images.PostUploadImageRoute
import net.perfectdreams.dreamstorageservice.routes.api.images.PutAllowedImageCropsOnImageRoute
import net.perfectdreams.dreamstorageservice.routes.api.images.PutImageLinkRoute
import net.perfectdreams.dreamstorageservice.tables.AllowedImageCrops
import net.perfectdreams.dreamstorageservice.tables.AuthorizationTokens
import net.perfectdreams.dreamstorageservice.tables.FileLinks
import net.perfectdreams.dreamstorageservice.tables.ImageLinks
import net.perfectdreams.dreamstorageservice.tables.ManipulatedStoredImages
import net.perfectdreams.dreamstorageservice.tables.StoredFiles
import net.perfectdreams.dreamstorageservice.tables.StoredImages
import net.perfectdreams.dreamstorageservice.utils.FailedToOptimizeImageException
import net.perfectdreams.dreamstorageservice.utils.FileUtils
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.DEFAULT_REPETITION_ATTEMPTS
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.io.IOException
import javax.print.attribute.standard.Compression

class DreamStorageService {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    val routes = listOf(
        GetFileFromFileLinkRoute(this),
        GetNamespacev1Route(this),
        GetNamespacev2Route(this),

        // ===[ FILES ]===
        GetFileLinksInfoRoute(this),
        PostUploadFileRoute(this),
        PutFileLinkRoute(this),
        DeleteFileLinkRoute(this),
        PostCheckFileRoute(this),

        // ===[ IMAGES ]===
        GetImageLinksInfoRoute(this),
        PostUploadImageRoute(this),
        PutImageLinkRoute(this),
        DeleteImageLinkRoute(this),
        PutAllowedImageCropsOnImageRoute(this),
        PostCheckImageRoute(this),
    )

    private val DRIVER_CLASS_NAME = "org.postgresql.Driver"
    private val ISOLATION_LEVEL = IsolationLevel.TRANSACTION_REPEATABLE_READ // We use repeatable read to avoid dirty and non-repeatable reads! Very useful and safe!!

    // Trying to manipulation all images at once won't work, it will just consume all memory and make the JVM exit with 137
    val imageManipulationProcessesSemaphore = Semaphore((Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1))

    private val typesToCache = listOf(
        ContentType.Text.CSS,
        ContentType.Text.JavaScript,
        ContentType.Application.JavaScript,
        ContentType.Image.Any,
        ContentType.Video.Any
    )

    val database = connectToDatabase(
        HikariDataSource(
            createPostgreSQLHikari(
                System.getenv("DSS_DATABASE_ADDRESS"),
                System.getenv("DSS_DATABASE_NAME"),
                System.getenv("DSS_DATABASE_USERNAME"),
                System.getenv("DSS_DATABASE_PASSWORD")
            )
        )
    )
    val fileUtils = FileUtils(this)

    fun start() {
        logger.info { "Using ${imageManipulationProcessesSemaphore.availablePermits} permits for image optimization" }

        runBlocking {
            transaction {
                SchemaUtils.createMissingTablesAndColumns(
                    StoredFiles,
                    StoredImages,
                    FileLinks,
                    ImageLinks,
                    ManipulatedStoredImages,
                    AuthorizationTokens,
                    AllowedImageCrops
                )
            }
        }

        embeddedServer(Netty, port = System.getenv("DSS_WEBSERVER_PORT")?.toInt() ?: 8080) {
            // Enables gzip and deflate compression
            install(Compression)

            // Enables caching for the specified types in the typesToCache list
            install(CachingHeaders) {
                options { _, outgoingContent ->
                    val contentType = outgoingContent.contentType
                    if (contentType != null) {
                        val contentTypeWithoutParameters = contentType.withoutParameters()
                        val matches = typesToCache.any { contentTypeWithoutParameters.match(it) || contentTypeWithoutParameters == it }

                        if (matches)
                            CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 365 * 24 * 3600))
                        else
                            null
                    } else null
                }
            }

            configureRouting(routes)
        }.start(wait = true)
    }

    /**
     * Deletes the [storedFile] from the database if there isn't any other [FileLinks] using this file.
     *
     * Must be called within a transaction! If this method called "transaction" directly, it would cause issues (because it would create a new transaction)
     */
    fun checkAndCleanUpFile(storedFileId: Long) {
        val count = FileLinks.select { FileLinks.storedFile eq storedFileId }
            .count()

        logger.info { "Stored File $storedFileId has $count references" }

        // Delete file because it is unused!
        if (count == 0L) {
            logger.info { "Deleting Stored File $storedFileId because there isn't any other link referencing it..." }
            StoredFiles.deleteWhere { StoredFiles.id eq storedFileId }
        }
    }

    /**
     * Deletes the [storedImageId] from the database if there isn't any other [FileLinks] using this file.
     *
     * Must be called within a transaction! If this method called "transaction" directly, it would cause issues (because it would create a new transaction)
     */
    fun checkAndCleanUpImage(storedImageId: Long) {
        val count = ImageLinks.select { ImageLinks.storedImage eq storedImageId }
            .count()

        logger.info { "Stored Image $storedImageId has $count references" }

        // Delete file because it is unused!
        if (count == 0L) {
            logger.info { "Deleting Stored Image $storedImageId because there isn't any other link referencing it..." }
            AllowedImageCrops.deleteWhere { AllowedImageCrops.storedImage eq storedImageId }
            ManipulatedStoredImages.deleteWhere { ManipulatedStoredImages.storedImage eq storedImageId }
            StoredImages.deleteWhere { StoredImages.id eq storedImageId }
        }
    }

    fun createPostgreSQLHikari(address: String, databaseName: String, username: String, password: String): HikariConfig {
        val hikariConfig = createHikariConfig()
        hikariConfig.jdbcUrl = "jdbc:postgresql://$address/$databaseName"

        hikariConfig.username = username
        hikariConfig.password = password

        return hikariConfig
    }

    private fun createHikariConfig(): HikariConfig {
        val hikariConfig = HikariConfig()

        hikariConfig.driverClassName = DRIVER_CLASS_NAME

        // Exposed uses autoCommit = false, so we need to set this to false to avoid HikariCP resetting the connection to
        // autoCommit = true when the transaction goes back to the pool, because resetting this has a "big performance impact"
        // https://stackoverflow.com/a/41206003/7271796
        hikariConfig.isAutoCommit = false

        // Useful to check if a connection is not returning to the pool, will be shown in the log as "Apparent connection leak detected"
        hikariConfig.leakDetectionThreshold = 30L * 1000
        hikariConfig.transactionIsolation = IsolationLevel.TRANSACTION_REPEATABLE_READ.name // We use repeatable read to avoid dirty and non-repeatable reads! Very useful and safe!!

        return hikariConfig
    }

    private fun connectToDatabase(dataSource: HikariDataSource): Database =
        Database.connect(
            HikariDataSource(dataSource),
            databaseConfig = DatabaseConfig {
                defaultRepetitionAttempts = DEFAULT_REPETITION_ATTEMPTS
                defaultIsolationLevel = ISOLATION_LEVEL.levelId // Change our default isolation level
            }
        )

    // https://github.com/JetBrains/Exposed/issues/1003
    suspend fun <T> transaction(repetitions: Int = 5, statement: suspend org.jetbrains.exposed.sql.Transaction.() -> T): T {
        var lastException: Exception? = null
        for (i in 1..repetitions) {
            try {
                return newSuspendedTransaction(Dispatchers.IO, database) {
                    statement.invoke(this)
                }
            } catch (e: ExposedSQLException) {
                logger.warn(e) { "Exception while trying to execute query. Tries: $i" }
                lastException = e
            }
        }
        throw lastException ?: RuntimeException("This should never happen")
    }

    suspend fun retrieveAuthenticationTokenFromNamespace(namespace: String): AuthorizationToken? {
        val validKey = transaction {
            AuthorizationTokens.select { AuthorizationTokens.namespace eq namespace }.firstOrNull()
        } ?: return null

        return AuthorizationToken(
            validKey[AuthorizationTokens.id],
            validKey[AuthorizationTokens.token],
            validKey[AuthorizationTokens.description],
            validKey[AuthorizationTokens.namespace]
        )
    }

    suspend fun optimizeImage(type: ContentType, data: ByteArray): ByteArray {
        require(type.contentType == "image") { "You can't optimize something that isn't a image!" }

        return when (type) {
            ContentType.Image.PNG -> optimizePNG(data)
            ContentType.Image.JPEG -> optimizeJPEG(data)
            else -> data
        }
    }

    private suspend fun optimizePNG(data: ByteArray): ByteArray {
        logger.info { "Optimizing PNG image, size = ${data.size}" }
        val proc = ProcessBuilder(
            System.getenv("DSS_PNGQUANT_PATH") ?: "/usr/bin/pngquant",
            "--quality=90-100",
            "--strip",
            "-"
        ).start()

        proc.outputStream.write(data)
        proc.outputStream.flush()
        proc.outputStream.close()

        logger.info { "Sent all data to pngquant, now we just need to wait until the image is optimized..." }
        val result = proc.inputStream.readAllBytes()
        val errorStreamResult = proc.errorStream.readAllBytes()

        val s = withContext(Dispatchers.IO) { proc.waitFor() }

        logger.info { "pngquant's error stream: ${errorStreamResult.toString(Charsets.UTF_8)}"}
        // https://manpages.debian.org/testing/pngquant/pngquant.1.en.html
        // 99 = if quality can't match what we want, pngquant exists with exit code 99
        if (s != 0 && s != 99) { // uuuh, this shouldn't happen if this is a PNG image...
            logger.warn { "Something went wrong while trying to optimize PNG image! Status = $s" }
            throw FailedToOptimizeImageException()
        }

        if (result.size >= data.size) {
            logger.info { "Tried optimizing the PNG image, but the original size ${data.size} is bigger than the optimized size ${result.size}!" }
            return data
        }

        logger.info { "Successfully optimized PNG image from ${data.size} to ${result.size}!" }
        return result
    }

    private suspend fun optimizeJPEG(data: ByteArray): ByteArray {
        logger.info { "Optimizing JPG image, size = ${data.size}" }
        val proc = ProcessBuilder(
            System.getenv("DSS_JPEGOPTIM_PATH") ?: "/usr/bin/jpegoptim",
            "-m95",
            "--strip-all",
            "--stdin",
            "--stdout"
        ).start()

        try {
            proc.outputStream.write(data)
        } catch (e: IOException) {
            // This may happen if the jpeg has "additional" data when it shouldn't!
            // The jpeg is optimized, but jpegoptim closes the pipe indicating that the file is already processed
            logger.warn(e) { "IOException while trying to optimize JPG image! This may indicate that the JPEG has additional content, so as long as the image was optimized, correctly, we will continue." }
        }
        proc.outputStream.flush()
        proc.outputStream.close()

        logger.info { "Sent all data to jpegoptim, now we just need to wait until the image is optimized..." }
        val result = proc.inputStream.readAllBytes()
        val errorStreamResult = proc.errorStream.readAllBytes()

        val s = withContext(Dispatchers.IO) { proc.waitFor() }

        logger.info { "jpegoptim's error stream: ${errorStreamResult.toString(Charsets.UTF_8)}"}
        if (s != 0) { // uuuh, this shouldn't happen if this is a JPG image...
            logger.warn { "Something went wrong while trying to optimize JPG image! Status = $s" }
            throw FailedToOptimizeImageException()
        }

        if (result.size >= data.size) {
            logger.info { "Tried optimizing the JPG image, but the original size ${data.size} is bigger than the optimized size ${result.size}!" }
            return data
        }

        logger.info { "Successfully optimized JPG image from ${data.size} to ${result.size}!" }
        return result
    }
}