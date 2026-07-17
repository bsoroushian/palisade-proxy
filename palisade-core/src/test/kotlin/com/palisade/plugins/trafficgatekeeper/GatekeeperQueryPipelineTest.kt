package com.palisade.plugins.trafficgatekeeper

import com.palisade.verifiers.MaxQueryParamCountVerifier
import com.palisade.verifiers.QueryParameterLengthVerifier
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Nested
import kotlin.test.*

class GatekeeperQueryPipelineTest {
    @Nested
    inner class QueryParameterLengthTest {
        @Test
        fun `should respond 400 when query parameter length boundary is broken`() =
            testGatekeeperApplication(
                configure = {
                    componentVerifiers =
                        arrayOf(
                            QueryParameterLengthVerifier(maxParamLengthProvider = { 5 }),
                        )
                },
            ) {
                val response = client.get("/test-endpoint?key=thisvalueiswaytoolong")

                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertEquals(
                    "An individual query key or value exceeds the allowable limit configured by Palisade.",
                    response.bodyAsText(),
                )
            }

        @Test
        fun `should pass successfully when parameters are well within limits`() =
            testGatekeeperApplication(
                configure = {
                    componentVerifiers =
                        arrayOf(
                            QueryParameterLengthVerifier(maxParamLengthProvider = { 5 }),
                        )
                },
            ) {
                val response = client.get("/test-endpoint?key=good")
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("SUCCESS", response.bodyAsText())
            }
    }

    @Nested
    inner class MaxQueryParamCountTest {
        @Test
        fun `should block the request if the number of query parameters exceed the limit`() =
            testGatekeeperApplication(
                configure = {
                    componentVerifiers =
                        arrayOf(
                            MaxQueryParamCountVerifier(maxQueryParamCountProvider = { 3 }),
                        )
                },
            ) {
                val response = client.get("/test-endpoint?a=1&b=2&c=3&d=4")
                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertEquals("The number of query parameters exceeds the allowable limit configured by Palisade.", response.bodyAsText())
            }

        @Test
        fun `should allow the request if the number of query parameters is less than the limit`() =
            testGatekeeperApplication(
                configure = {
                    componentVerifiers =
                        arrayOf(
                            MaxQueryParamCountVerifier(maxQueryParamCountProvider = { 3 }),
                        )
                },
            ) {
                val response = client.get("/test-endpoint?a=1&b=2")
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("SUCCESS", response.bodyAsText())
            }
    }
}
