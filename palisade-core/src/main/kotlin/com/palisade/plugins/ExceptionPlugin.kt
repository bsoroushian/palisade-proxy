package com.palisade.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.http.IllegalHeaderValueException
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.micrometer.core.instrument.Counter

private val headerExploitCounter: Counter =
    Counter.builder("gateway_blocked_requests_total")
        .description("The total number of malicious requests dropped by the API gateway guards")
        .tag("reason", "illegal_header_exception") // Clear distinction from standard 400 errors
        .register(AppMetricsRegistry)

fun Application.configureExceptionHandling() {
    install(StatusPages) {
        // Catches both inbound parsing anomalies AND outbound client replication crashes
        exception<IllegalHeaderValueException> { call, cause ->
            headerExploitCounter.increment()

            call.respond(HttpStatusCode.BadRequest, "Malicious, malformed, or invalid characters detected in headers.")
        }

        exception<BadRequestException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, "Malformed request structure or illegal query parameters.")
        }

        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, "Malformed URL components or query encoding.")
        }
    }
}
