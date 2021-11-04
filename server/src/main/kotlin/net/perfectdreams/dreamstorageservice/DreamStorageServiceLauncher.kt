package net.perfectdreams.dreamstorageservice

object DreamStorageServiceLauncher {
    @JvmStatic
    fun main(args: Array<String>) {
        val s = DreamStorageService()
        s.start()
    }
}