package com.palisade.core

import com.palisade.config.ConfigManager
import com.palisade.config.PalisadeConfig
import com.palisade.plugins.TrafficGatekeeper
import com.palisade.plugins.configureExceptionHandling
import com.palisade.plugins.configureMetrics
import com.palisade.plugins.configureSecurity
import com.palisade.plugins.configureSerialization
import com.palisade.routes.managementRoutes
import com.palisade.routes.proxyRouting
import com.palisade.routes.testLoopbackRoutes
import com.palisade.verifiers.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.network.tls.certificates.generateCertificate
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.CommandLineConfig
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import java.io.File
import java.security.KeyStore

fun main(args: Array<String>) {
    val keyStorePassword = "password"
    val keyAlias = "temp-dev-key"
    val trustStore = getOrCreateDevKeyStore(keyAlias, keyStorePassword)
    val commandLineConfig = CommandLineConfig(args)

    val server =
        embeddedServer(
            factory = Netty,
            environment = commandLineConfig.environment,
            configure = { takeFrom(commandLineConfig.engineConfig) },
        )

    server.start(wait = true)
}

fun Application.module() {
    val cfg = PalisadeConfig.load(environment.config)
    ConfigManager.initialize(cfg)
    configureSerialization()
    configureSecurity()
    configureMetrics()
    configureExceptionHandling()

    install(TrafficGatekeeper) {
        methodVerifiers = arrayOf(AllowedMethodsVerifier())
        componentVerifiers =
            arrayOf(
                MaxQueryParamCountVerifier(),
                AllowedPathPrefixVerifier(),
                ForbiddenPathSegmentsVerifier(),
                QueryParameterLengthVerifier(),
            )
        headerVerifiers =
            arrayOf(
                ProhibitedHeaderVerifier(),
                SingleValueHeaderVerifier(),
                MaxHeaderSizeVerifier(),
            )
    }

    routing {
        managementRoutes()
        testLoopbackRoutes()
        proxyRouting(HttpClient(CIO))
    }
}

fun getOrCreateDevKeyStore(
    alias: String,
    pass: String,
): KeyStore {
    val file =
        File("build/temporary-dev.jks").apply {
            parentFile?.mkdirs()
        }

    // Generate the certificate dynamically if missing
    if (!file.exists()) {
        println("⚠️ SSL Keystore not found. Generating a temporary self-signed certificate...")
        generateCertificate(
            file = file,
            keyAlias = alias,
            keyPassword = pass,
            jksPassword = pass,
            algorithm = "SHA256withRSA",
        )
        println("✅ Temporary certificate created at: ${file.absolutePath}")
    }

    // Load the certificate structure
    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
    file.inputStream().use { stream ->
        keyStore.load(stream, pass.toCharArray())
    }
    return keyStore
}
