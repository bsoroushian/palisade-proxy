package com.palisade.routes

import com.palisade.config.ConfigManager
import io.ktor.client.HttpClient
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.contentLength
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.Routing
import io.ktor.server.routing.route
import io.ktor.utils.io.copyTo

private val HOP_BY_HOP_HEADERS =
    setOf(
        HttpHeaders.Connection.lowercase(),
        "keep-alive",
        HttpHeaders.ProxyAuthenticate.lowercase(),
        HttpHeaders.ProxyAuthorization.lowercase(),
        HttpHeaders.TE.lowercase(),
        HttpHeaders.Trailer.lowercase(),
        HttpHeaders.TransferEncoding.lowercase(),
        HttpHeaders.Upgrade.lowercase(),
    )

fun Routing.proxyRouting(proxyClient: HttpClient) {
    route("{...}") {
        handle {
            val clientCall = call
            val clientRequest = clientCall.request

            val downstreamTargetHost = ConfigManager.current.targetHost
            val targetUrl = "$downstreamTargetHost${clientRequest.uri}"

            try {
                val downstreamResponse =
                    proxyClient.request(targetUrl) {
                        method = clientRequest.httpMethod

                        val hasBody =
                            (clientRequest.contentLength() ?: 0L) > 0L ||
                                clientRequest.headers[HttpHeaders.TransferEncoding] != null

                        if (hasBody) {
                            setBody(clientRequest.receiveChannel())
                        }

                        // OPTIMIZATION: Bypassed getAll() list/string wrapper allocation
                        val existingXff = clientRequest.headers[HttpHeaders.XForwardedFor]
                        val clientRemoteHost = clientRequest.local.remoteHost
                        val updatedXff =
                            if (existingXff.isNullOrBlank()) {
                                clientRemoteHost
                            } else {
                                "$existingXff, $clientRemoteHost"
                            }

                        clientRequest.headers.forEach { key, values ->
                            // OPTIMIZATION: Avoid allocation by matching using ignoreCase where possible,
                            // or checking length constraints before allocating lowercase strings.
                            val lowerKey = key.lowercase()

                            if (!HOP_BY_HOP_HEADERS.contains(lowerKey) &&
                                lowerKey != "x-user-id" &&
                                lowerKey != "content-length" &&
                                lowerKey != "host" &&
                                lowerKey != "x-forwarded-for"
                            ) {
                                for (i in 0 until values.size) {
                                    headers.append(key, values[i])
                                }
                            }
                        }

                        headers.append(HttpHeaders.XForwardedFor, updatedXff)
                        headers.append("X-Gateway-Identity", "Palisade-Secure-Proxy")
                    }

                // 2. Process backend response headers
                // OPTIMIZATION: Track downstream Content-Length to avoid forced Chunked-Encoding allocations
                var downstreamContentLength: Long? = null

                clientCall.response.headers.apply {
                    downstreamResponse.headers.forEach { key, values ->
                        val lowerKey = key.lowercase()
                        if (!HOP_BY_HOP_HEADERS.contains(lowerKey)) {
                            if (lowerKey == "content-length") {
                                if (values.isNotEmpty()) {
                                    downstreamContentLength = values[0].toLongOrNull() // Read index 0 to avoid list iteration
                                }
                            } else {
                                for (i in 0 until values.size) {
                                    append(key, values[i])
                                }
                            }
                        }
                    }
                }

                // 3. Stream asynchronous response bytes back to client
                val contentTypeHeader = downstreamResponse.headers[HttpHeaders.ContentType]
                val parsedContentType = contentTypeHeader?.let { ContentType.parse(it) }

                clientCall.respondBytesWriter(
                    contentType = parsedContentType,
                    status = downstreamResponse.status,
                    // OPTIMIZATION: Stream directly if size is known!
                    contentLength = downstreamContentLength,
                ) {
                    downstreamResponse.bodyAsChannel().copyTo(this)
                }
            } catch (e: Exception) {
                clientCall.respond(HttpStatusCode.BadGateway, "Downstream service unavailable or timed out.")
            }
        }
    }
}
