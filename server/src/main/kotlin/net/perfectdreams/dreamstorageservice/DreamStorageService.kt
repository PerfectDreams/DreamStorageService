package net.perfectdreams.dreamstorageservice

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.util.IsolationLevel
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import net.perfectdreams.dreamstorageservice.plugins.configureRouting
import net.perfectdreams.dreamstorageservice.routes.DeleteFileLinkRoute
import net.perfectdreams.dreamstorageservice.routes.GetFileFromFileLinkRoute
import net.perfectdreams.dreamstorageservice.routes.GetNamespaceRoute
import net.perfectdreams.dreamstorageservice.routes.PutAllowedImageCropsOnFileRoute
import net.perfectdreams.dreamstorageservice.routes.PutUploadFileRoute
import net.perfectdreams.dreamstorageservice.tables.AllowedImageCrops
import net.perfectdreams.dreamstorageservice.tables.AuthorizationTokens
import net.perfectdreams.dreamstorageservice.tables.FileLinks
import net.perfectdreams.dreamstorageservice.tables.ManipulatedStoredFiles
import net.perfectdreams.dreamstorageservice.tables.StoredFiles
import net.perfectdreams.dreamstorageservice.utils.FileUtils
import net.perfectdreams.dreamstorageservice.utils.UploadedAsFileType
import net.perfectdreams.dreamstorageservice.utils.exposed.createOrUpdatePostgreSQLEnum
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.DEFAULT_REPETITION_ATTEMPTS
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class DreamStorageService {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    val routes = listOf(
        PutUploadFileRoute(this),
        DeleteFileLinkRoute(this),
        GetFileFromFileLinkRoute(this),
        GetNamespaceRoute(this),
        PutAllowedImageCropsOnFileRoute(this)
    )

    private val DRIVER_CLASS_NAME = "org.postgresql.Driver"
    private val ISOLATION_LEVEL = IsolationLevel.TRANSACTION_REPEATABLE_READ // We use repeatable read to avoid dirty and non-repeatable reads! Very useful and safe!!

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
        runBlocking {
            transaction {
                createOrUpdatePostgreSQLEnum(UploadedAsFileType.values())
                SchemaUtils.createMissingTablesAndColumns(
                    StoredFiles,
                    FileLinks,
                    ManipulatedStoredFiles,
                    AuthorizationTokens,
                    AllowedImageCrops
                )
            }
        }

        embeddedServer(Netty, port = 8080) {
            // Enables gzip and deflate compression
            install(Compression)

            // Enables caching for the specified types in the typesToCache list
            install(CachingHeaders) {
                options { outgoingContent ->
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
            AllowedImageCrops.deleteWhere { AllowedImageCrops.storedFile eq storedFileId }
            ManipulatedStoredFiles.deleteWhere { ManipulatedStoredFiles.storedFile eq storedFileId }
            StoredFiles.deleteWhere { StoredFiles.id eq storedFileId }
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
            "--quality=100",
            "--strip",
            "-"
        ).start()

        proc.outputStream.write(data)
        proc.outputStream.flush()
        proc.outputStream.close()

        val result = proc.inputStream.readAllBytes()

        val s = withContext(Dispatchers.IO) { proc.waitFor() }
        if (s != 0) { // uuuh, this shouldn't happen if this is a PNG image...
            logger.warn { "Something went wrong while trying to optimize PNG image! Status = $s" }
            return data
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

        proc.outputStream.write(data)
        proc.outputStream.flush()
        proc.outputStream.close()

        val result = proc.inputStream.readAllBytes()

        val s = withContext(Dispatchers.IO) { proc.waitFor() }
        if (s != 0) { // uuuh, this shouldn't happen if this is a JPG image...
            logger.warn { "Something went wrong while trying to optimize JPG image! Status = $s" }
            return data
        }

        if (result.size >= data.size) {
            logger.info { "Tried optimizing the JPG image, but the original size ${data.size} is bigger than the optimized size ${result.size}!" }
            return data
        }

        logger.info { "Successfully optimized JPG image from ${data.size} to ${result.size}!" }
        return result
    }
}