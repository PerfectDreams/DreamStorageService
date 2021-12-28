package net.perfectdreams.dreamstorageservice.routes.api

import net.perfectdreams.sequins.ktor.BaseRoute

abstract class VersionedAPIRoute(path: String) : BaseRoute(
    "/api/v2$path",
)