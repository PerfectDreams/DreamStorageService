package net.perfectdreams.dreamstorageservice.tables

import org.jetbrains.exposed.sql.javatime.timestamp

object FileLinks : TextIdTable() {
    val createdAt = timestamp("created_at")
    val storedFile = reference("stored_file", StoredFiles).index()
}