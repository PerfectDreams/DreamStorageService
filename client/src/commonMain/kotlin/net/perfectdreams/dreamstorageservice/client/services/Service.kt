package net.perfectdreams.dreamstorageservice.client.services

import net.perfectdreams.dreamstorageservice.client.DreamStorageServiceClient

open class Service(internal val client: DreamStorageServiceClient) {
    internal val apiVersion = "v1"
}