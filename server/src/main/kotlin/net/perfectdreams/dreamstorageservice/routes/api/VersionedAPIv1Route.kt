package net.perfectdreams.dreamstorageservice.routes.api

import net.perfectdreams.sequins.ktor.BaseRoute

abstract class VersionedAPIv1Route(path: String) : BaseRoute(
    "/api/v1$path",
)