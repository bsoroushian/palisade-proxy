package com.palisade.plugins.trafficgatekeeper

import com.palisade.verifiers.AllowedMethodsVerifier
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

class GatekeeperMethodPipelineTest {
    @Test
    fun `should block http request methods that are not explicitly allowed`() =
        testGatekeeperApplication(
            configure = {
                methodVerifiers =
                    arrayOf(
                        AllowedMethodsVerifier(allowedMethodsProvider = { setOf("GET", "POST", "PUT") }),
                    )
            },
        ) {
            val response =
                client.request("/test-endpoint?key=good") {
                    method = HttpMethod.Trace
                }
            assertEquals(HttpStatusCode.MethodNotAllowed, response.status)
            assertEquals("Method Not Allowed by Palisade perimeter shield.", response.bodyAsText())
        }

    @Test
    fun `should allow http request methods that are explicitly whitelisted`() =
        testGatekeeperApplication(
            configure = {
                methodVerifiers =
                    arrayOf(
                        AllowedMethodsVerifier(allowedMethodsProvider = { setOf("GET", "POST", "PUT") }),
                    )
            },
        ) {
            val response = client.get("/test-endpoint?key=good")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("SUCCESS", response.bodyAsText())
        }
}
