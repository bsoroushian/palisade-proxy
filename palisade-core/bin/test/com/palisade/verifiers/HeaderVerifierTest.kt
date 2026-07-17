package com.palisade.verifiers

import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Nested
import kotlin.test.*

class HeaderVerifierTest {
    @Nested
    inner class ForbiddenHeaderPreventationTest {
        private val verifier = ProhibitedHeaderVerifier(forbiddenHeadersProvider = { setOf("x-prohibited") })

        @Test
        fun `should return badRequest for requests having prohibited headers`() =
            runBlocking {
                val result = verifier.verify("x-prohibited", listOf("some-value"))

                assertIs<VerificationResult.Denied>(result)
                assertEquals(HttpStatusCode.BadRequest, result.status)
                assertContains(result.message, "Prohibited header detected")
            }

        @Test
        fun `should safely let requests that don't have forbidden headers`() =
            runBlocking {
                val result = verifier.verify("x-allowed-header", listOf("some-value"))

                assertIs<VerificationResult.Allowed>(result)
            }
    }

    @Nested
    inner class SingleValueHeaderVerifierTest {
        private val testProvider = { setOf("host", "content-length", "x-forwarded-for") }
        private val verifier = SingleValueHeaderVerifier(singleValueHeadersProvider = testProvider)

        @Test
        fun `should allow standard unmanipulated single value header`() =
            runBlocking {
                val result = verifier.verify("host", listOf("palisade-proxy.internal"))

                assertIs<VerificationResult.Allowed>(result)
            }

        @Test
        fun `should completely ignore headers not present in the strict config set`() =
            runBlocking {
                // Even with multiple elements or commas, if it's not guarded, it should pass
                val result = verifier.verify("user-agent", listOf("Mozilla, Chrome, Safari"))

                assertIs<VerificationResult.Allowed>(result)
            }

        @Test
        fun `should deny when multiple header items are injected as a duplicate list`() =
            runBlocking {
                // Simulating multiple lines: Host: a.com \n Host: b.com
                val result = verifier.verify("host", listOf("victim.com", "attacker.com"))

                assertIs<VerificationResult.Denied>(result)

                assertEquals(HttpStatusCode.BadRequest, result.status)
                assertContains(result.message, "Duplicate header injection")
            }

        @Test
        fun `should deny when a single header item contains a hidden comma separator`() =
            runBlocking {
                // Simulating standard HTTP parameter splitting: Host: victim.com,attacker.com
                val result = verifier.verify("host", listOf("victim.com,attacker.com"))

                assertIs<VerificationResult.Denied>(result)

                assertEquals(HttpStatusCode.BadRequest, result.status)
                assertContains(result.message, "Multi-valued comma separation")
            }
    }

    @Nested
    inner class MaxHeaderSizeValidationTest {
        private val verifier = MaxHeaderSizeVerifier(maxHeaderSizeProvider = { 20 })

        @Test
        fun `should return payload too large if header values size exceed the limit`() =
            runBlocking {
                val result = verifier.verify("x-long-header", listOf("this is a very long header value that exceed limit"))

                assertIs<VerificationResult.Denied>(result)
                assertEquals(result.status, HttpStatusCode.PayloadTooLarge)
                assertContains(result.message, "Individual header 'x-long-header' total size")
            }

        @Test
        fun `should prevent a request if header has a name that exceed the limit`() =
            runBlocking {
                val result = verifier.verify("x-header-with-a-very-long-name", listOf("val"))

                assertIs<VerificationResult.Denied>(result)
                assertEquals(result.status, HttpStatusCode.PayloadTooLarge)
                assertContains(result.message, "Individual header 'x-header-with-a-very-long-name' total size")
            }

        @Test
        fun `should block a request if sum of different values exceed the limit`() =
            runBlocking {
                val result = verifier.verify("x-header", listOf("value1", "value2", "value3"))

                assertIs<VerificationResult.Denied>(result)
                assertEquals(result.status, HttpStatusCode.PayloadTooLarge)
                assertContains(result.message, "Individual header 'x-header' total size")
            }

        @Test
        fun `should allow headers with size below the limit`() =
            runBlocking {
                val result = verifier.verify("x-header", listOf("value1"))

                assertIs<VerificationResult.Allowed>(result)
            }
    }
}
