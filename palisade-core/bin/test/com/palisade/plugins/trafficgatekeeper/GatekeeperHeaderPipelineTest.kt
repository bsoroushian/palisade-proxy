package com.palisade.plugins.trafficgatekeeper

import com.palisade.verifiers.MaxHeaderSizeVerifier
import com.palisade.verifiers.ProhibitedHeaderVerifier
import com.palisade.verifiers.SingleValueHeaderVerifier
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Nested
import kotlin.test.*

class GatekeeperHeaderPipelineTest {
    @Nested
    inner class ForbiddenHeadersProhibitionTest {
        @Test
        fun `should prevent passing of requests that have headers listed as forbidden`() =
            testGatekeeperApplication(
                configure = {
                    headerVerifiers =
                        arrayOf(
                            ProhibitedHeaderVerifier(forbiddenHeadersProvider = { setOf("host", "x-malicious-header", "x-forwarded-for") }),
                        )
                },
            ) {
                val response =
                    client.get("/test") {
                        header("x-malicious-header", "header-value")
                    }

                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertContains(response.bodyAsText(), "Prohibited header detected")
            }

        @Test
        fun `should allow passing of requests that without headers listed as forbidden`() =
            testGatekeeperApplication(
                configure = {
                    headerVerifiers =
                        arrayOf(
                            ProhibitedHeaderVerifier(forbiddenHeadersProvider = { setOf("host", "x-malicious-header", "x-forwarded-for") }),
                        )
                },
            ) {
                val response =
                    client.get("/test") {
                        header("x-header-1", "header1-value")
                        header("x-header-2", "header2-value")
                    }

                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals(response.bodyAsText(), "SUCCESS")
            }
    }

    @Nested
    inner class SingleValueHeaderTest {
        @Test
        fun `should prevent passing of requests that have more than value for headers set as restricted`() =
            testGatekeeperApplication(
                configure = {
                    headerVerifiers =
                        arrayOf(
                            SingleValueHeaderVerifier(singleValueHeadersProvider = { setOf("host", "x-singlevalue-header") }),
                        )
                },
            ) {
                val response =
                    client.get("/test") {
                        header("x-singlevalue-header", "Value1, Value2")
                    }

                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertContains(response.bodyAsText(), "Multi-valued comma separation detected in strict single-value header")
            }

        @Test
        fun `should prevent passing of requests that have restricted header appear more than once`() =
            testGatekeeperApplication(
                configure = {
                    headerVerifiers =
                        arrayOf(
                            SingleValueHeaderVerifier(singleValueHeadersProvider = { setOf("host", "x-singlevalue-header") }),
                        )
                },
            ) {
                val response =
                    client.get("/test") {
                        header("x-singlevalue-header", "Value1")
                        header("x-singlevalue-header", "Value2")
                    }

                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertContains(response.bodyAsText(), "Multi-valued comma separation detected in strict single-value header")
            }

        @Test
        fun `should allow passing of requests that have restricted header with only one value`() =
            testGatekeeperApplication(
                configure = {
                    headerVerifiers =
                        arrayOf(
                            SingleValueHeaderVerifier(singleValueHeadersProvider = { setOf("host", "x-singlevalue-header") }),
                        )
                },
            ) {
                val response =
                    client.get("/test") {
                        header("x-singlevalue-header", "Only-Value")
                    }

                assertEquals(HttpStatusCode.OK, response.status)
                assertContains(response.bodyAsText(), "SUCCESS")
            }
    }

    @Nested
    inner class MaxHeaderSizeTest {
        @Test
        fun `should block a request that one of its headers has excessive size bigger than limit`() =
            testGatekeeperApplication(
                configure = {
                    headerVerifiers =
                        arrayOf(
                            MaxHeaderSizeVerifier(maxHeaderSizeProvider = { 20 }),
                        )
                },
            ) {
                val response =
                    client.get("/test") {
                        header("x-header", "thisvalueistoolongforlimitsetforasingleheader")
                    }

                assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
                assertContains(response.bodyAsText(), "Individual header 'x-header' total size")
            }

        @Test
        fun `prevents passing of requests with header name too long`() =
            testGatekeeperApplication(
                configure = {
                    headerVerifiers =
                        arrayOf(
                            MaxHeaderSizeVerifier(maxHeaderSizeProvider = { 20 }),
                        )
                },
            ) {
                val response =
                    client.get("/test") {
                        header("x-averylongnameforaheader-shouldbeblocked", "val")
                    }

                assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
                assertContains(response.bodyAsText(), "Individual header")
                assertContains(response.bodyAsText(), "exceeds the allowed limit of")
            }

        @Test
        fun `allows passing of requests which have header with size smaller than set limit`() =
            testGatekeeperApplication(
                configure = {
                    headerVerifiers =
                        arrayOf(
                            MaxHeaderSizeVerifier(maxHeaderSizeProvider = { 40 }),
                        )
                },
            ) {
                val response =
                    client.get("/test") {
                        header("x-header", "noraml-value")
                    }

                assertEquals(HttpStatusCode.OK, response.status)
                assertContains(response.bodyAsText(), "SUCCESS")
            }
    }

    @Nested
    inner class MaxHeaderCountTest {
        @Test
        fun `should drop request when header count breaks boundary`() =
            testGatekeeperApplication(
                configure = {
                    maxHeaderCountProvider = { 2 }
                },
            ) {
                val response =
                    client.get("/test-endpoint") {
                        header("X-Header-One", "1")
                        header("X-Header-Two", "2")
                        header("X-Header-Three", "3")
                    }

                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertContains(response.bodyAsText(), "headers exceed the limit of")
            }
    }
}
