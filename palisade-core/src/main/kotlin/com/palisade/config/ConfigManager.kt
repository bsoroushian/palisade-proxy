package com.palisade.config

import io.ktor.server.config.*
import java.util.concurrent.atomic.AtomicReference

object ConfigManager {
    private val configRef = AtomicReference<PalisadeConfig>()

    val current: PalisadeConfig
        get() = configRef.get() ?: throw IllegalStateException("ConfigManager not initialized")

    fun initialize(config: PalisadeConfig) {
        configRef.set(config)
    }

    fun reloadFromFile(newConfig: PalisadeConfig) {
        configRef.set(newConfig) // Simple set is perfect here
    }

    fun patchConfig(transform: (PalisadeConfig) -> PalisadeConfig): PalisadeConfig {
        return configRef.updateAndGet { currentConfig ->
            transform(currentConfig)
        }
    }
}
