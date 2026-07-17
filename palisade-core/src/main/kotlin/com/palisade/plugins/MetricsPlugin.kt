package com.palisade.plugins

import io.ktor.server.application.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry

// Expose the registry globally so other files/guards can access it
val AppMetricsRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

fun Application.configureMetrics() {
    install(MicrometerMetrics) {
        registry = AppMetricsRegistry
    }
}
