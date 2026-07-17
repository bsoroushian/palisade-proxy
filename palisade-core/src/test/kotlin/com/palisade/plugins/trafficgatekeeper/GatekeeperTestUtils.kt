package com.palisade.plugins.trafficgatekeeper

import com.palisade.config.ConfigManager
import com.palisade.config.PalisadeConfig
import com.palisade.plugins.TrafficGatekeeper
import com.palisade.plugins.TrafficGatekeeperConfig
import io.ktor.client.plugins.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*

val testConfig =
    PalisadeConfig(
        targetHost = "https://localhost:9999",
        allowedMethods = setOf("GET", "POST"),
    )

// Reusable test harness that boots TrafficGatekeeper instantly
fun testGatekeeperApplication(
    configure: TrafficGatekeeperConfig.() -> Unit,
    block: suspend ApplicationTestBuilder.() -> Unit,
) = testApplication {
    application {
        ConfigManager.initialize(testConfig)
        // Automatically installs the plugin under test
        install(TrafficGatekeeper) {
            configure()
        }

        // Dummy routing endpoint to verify requests that successfully pass through
        routing {
            route("{...}") {
                handle {
                    call.respondText("SUCCESS")
                }
            }
        }
    }
    block()
}
