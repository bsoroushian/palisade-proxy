package com.palisade.plugins

import com.palisade.config.ConfigManager
import io.ktor.server.application.*
import io.ktor.server.auth.*
import org.mindrot.jbcrypt.BCrypt

fun Application.configureSecurity() {
    install(Authentication) {
        basic("palisade-admin-auth") {
            realm = "Palisade Secure Operations"
            validate { credentials ->
                val expectedHash = ConfigManager.current.superAdminPasswordHash

                // Validate user is 'admin' and the password dynamically matches the BCrypt hash
                if (credentials.name == "admin" && BCrypt.checkpw(credentials.password, expectedHash)) {
                    UserIdPrincipal(credentials.name)
                } else {
                    null
                }
            }
        }
    }
}
