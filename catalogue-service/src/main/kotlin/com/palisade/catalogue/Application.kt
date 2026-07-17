package com.palisade.catalogue

import java.io.File
import java.security.KeyStore
import io.ktor.network.tls.certificates.generateCertificate
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.basic
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.Database
import com.palisade.catalogue.db.seedDatabase
import com.palisade.catalogue.routing.configureRouting

fun main() {
   embeddedServer(Netty, port = 8081, module = Application::module).start(wait = true)
}


fun Application.module() {
    Database.connect(
        url = "jdbc:h2:mem:mock;DB_CLOSE_DELAY=-1;MODE=PostgreSQL", 
        driver = "org.h2.Driver"
    )

    seedDatabase()

    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        })
    }

    install(Authentication) {
        basic("basic-auth") {
            realm = "Mock API"

            validate { credentials ->
                val username = credentials.name
                val password = credentials.password

                // Mock users (plain text)
                val ok = (!username.isNullOrBlank() && password == "pass123")

                if (ok) UserIdPrincipal(username) else null
            }
        }
    }

    configureRouting()
}
