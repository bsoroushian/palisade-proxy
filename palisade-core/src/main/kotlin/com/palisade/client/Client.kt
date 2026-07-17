package com.palisade.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

fun createInsecureHttpClient(): HttpClient {
    return HttpClient(CIO) {
        engine {
            https {
                // Equivalent to curl -k / --insecure
                trustManager =
                    object : X509TrustManager {
                        override fun checkClientTrusted(
                            chain: Array<out X509Certificate>?,
                            authType: String?,
                        ) {}

                        override fun checkServerTrusted(
                            chain: Array<out X509Certificate>?,
                            authType: String?,
                        ) {}

                        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                    }
            }
        }
    }
}
