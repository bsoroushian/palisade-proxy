package com.palisade.routes

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Routing.testLoopbackRoutes() {
    val currentEnv = System.getenv("PALISADE_ENV")
    if (currentEnv == "test") {
        get("/palisade-test-loopback") {
            call.respondText("PALISADE_PASSED")
        }
    }
}
