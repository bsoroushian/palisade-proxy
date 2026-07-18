package com.palisade.routes

import com.palisade.config.ConfigManager
import com.palisade.config.PalisadeConfig
import com.palisade.plugins.AppMetricsRegistry
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

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
                val config = ConfigManager.current

                // 1. Serialize your current config object down into a generic JsonElement
                val originalJsonElement = Json.encodeToJsonElement(PalisadeConfig.serializer(), config)

                // 2. Build a brand new JsonObject, copying everything except your target key
                val safeJson =
                    buildJsonObject {
                        originalJsonElement.jsonObject.forEach { (key, value) ->
                            if (key != "superAdminPasswordHash") {
                                put(key, value)
                            }
                        }
                    }

                // 3. Directly respond with the clean payload
                call.respond(HttpStatusCode.OK, safeJson)
            }
        }
    }
}
