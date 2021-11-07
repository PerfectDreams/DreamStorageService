package net.perfectdreams.dreamstorageservice.utils

import net.perfectdreams.dreamstorageservice.DreamStorageService
import java.security.MessageDigest

class FileUtils(val m: DreamStorageService) {
       // MessageDigest is not thread safe!
    fun calculateChecksum(array: ByteArray) = MessageDigest.getInstance("SHA-256").digest(array)
}