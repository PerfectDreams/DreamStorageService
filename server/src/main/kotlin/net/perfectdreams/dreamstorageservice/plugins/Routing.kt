package net.perfectdreams.dreamstorageservice.plugins

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.routing.*
import net.perfectdreams.sequins.ktor.BaseRoute

fun Application.configureRouting(routes: List<BaseRoute>) {
    routing {
        trace {
            println(it.buildText())
        }

        for (route in routes)
            route.register(this)
    }
}