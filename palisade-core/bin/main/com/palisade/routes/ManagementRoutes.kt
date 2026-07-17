package com.palisade.routes

import com.palisade.config.ConfigManager
import com.palisade.plugins.AppMetricsRegistry
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Routing.managementRoutes() {
    route("/_palisade") {
        get("/metrics") {
            call.respondText(AppMetricsRegistry.scrape())
        }

        // You can easily add health checks or status endpoints here later
        get("/health") {
            call.respondText("OK")
        }

        authenticate("palisade-admin-auth") {
            get("/config") {
                // No DAO required! Automatically serializes to JSON via @Serializable
                val config = ConfigManager.current
                call.respond(HttpStatusCode.OK, config)
            }
        }
    }
}
