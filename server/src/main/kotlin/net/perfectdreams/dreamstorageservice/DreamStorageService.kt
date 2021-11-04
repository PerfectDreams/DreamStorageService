package net.perfectdreams.dreamstorageservice

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.util.IsolationLevel
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import net.perfectdreams.dreamstorageservice.plugins.configureRouting
import net.perfectdreams.dreamstorageservice.routes.DeleteFileLinkRoute
import net.perfectdreams.dreamstorageservice.routes.GetFileFromFileLinkRoute
import net.perfectdreams.dreamstorageservice.routes.PutUploadFileRoute
import net.perfectdreams.dreamstorageservice.tables.AuthorizationTokens
import net.perfectdreams.dreamstorageservice.tables.FileLinks
import net.perfectdreams.dreamstorageservice.tables.StoredFiles
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.DEFAULT_REPETITION_ATTEMPTS
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

class DreamStorageService {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    val routes = listOf(
        PutUploadFileRoute(this),
        DeleteFileLinkRoute(this),
        GetFileFromFileLinkRoute(this),
    )

    private val DRIVER_CLASS_NAME = "org.postgresql.Driver"
    private val ISOLATION_LEVEL = IsolationLevel.TRANSACTION_REPEATABLE_READ // We use repeatable read to avoid dirty and non-repeatable reads! Very useful and safe!!

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

    fun start() {
        runBlocking {
            transaction {
                SchemaUtils.createMissingTablesAndColumns(
                    StoredFiles,
                    FileLinks,
                    AuthorizationTokens
                )
            }
        }

        embeddedServer(Netty, port = 8080) {
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
}