package com.palisade.plugins.trafficgatekeeper

import com.palisade.verifiers.AllowedPathPrefixVerifier
import com.palisade.verifiers.ForbiddenPathSegmentsVerifier
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Nested
import kotlin.test.*

class GatekeeperPathPipelineTest {
    @Nested
    inner class WhiteListedPathPrefixesTest {
        @Test
        fun `should respond 404 when path prefix is not whitelisted`() =
            testGatekeeperApplication(
                configure = {
                    componentVerifiers =
                        arrayOf(
                            AllowedPathPrefixVerifier(allowedPrefixesProvider = { arrayOf("/test/api") }),
                        )
                },
            ) {
                val response = client.get("/test-endpoint?key=good")
                assertEquals(HttpStatusCode.NotFound, response.status)
                assertEquals("The requested resource does not exist or is protected by Palisade perimeter shield.", response.bodyAsText())
            }

        @Test
        fun `should allow the request if uri prefix is not whitelisted`() =
            testGatekeeperApplication(
                configure = {
                    componentVerifiers =
                        arrayOf(
                            AllowedPathPrefixVerifier(allowedPrefixesProvider = { arrayOf("/test/api") }),
                        )
                },
            ) {
                val response = client.get("/test/api/books?id=100")
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("SUCCESS", response.bodyAsText())
            }
    }

    @Nested
    inner class ForbiddenPathSegmentsTest {
        @Test
        fun `should denie the request that has forbidden segment in its path`() =
            testGatekeeperApplication(
                configure = {
                    componentVerifiers =
                        arrayOf(
                            ForbiddenPathSegmentsVerifier(forbiddenSegmentsProvider = { arrayOf("/../", "//") }),
                        )
                },
            ) {
                val response = client.get("/test/api/../1")
                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertEquals("Malicious request path structure detected", response.bodyAsText())
            }

        @Test
        fun `should safely allow a request that has not any restricted segment in its path`() =
            testGatekeeperApplication(
                configure = {
                    componentVerifiers =
                        arrayOf(
                            ForbiddenPathSegmentsVerifier(forbiddenSegmentsProvider = { arrayOf("/../", "//") }),
                        )
                },
            ) {
                val response = client.get("/test/api/record")
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("SUCCESS", response.bodyAsText())
            }
    }
}
