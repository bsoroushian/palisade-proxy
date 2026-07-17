package com.palisade.verifiers

import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Nested
import kotlin.test.*

class AllowedMethodsVerifierTest {
    @Nested
    inner class HttpMethodValidation {
        @Test
        fun `should allow valid http methods`() =
            runBlocking {
                val verifier =
                    AllowedMethodsVerifier(
                        allowedMethodsProvider = { setOf("GET", "POST") },
                    )

                val result = verifier.verify("GET")

                assertIs<VerificationResult.Allowed>(result)
            }

        @Test
        fun `should deny forbidden HTTP methods`() =
            runBlocking {
                val verifier =
                    AllowedMethodsVerifier(
                        allowedMethodsProvider = { setOf("GET", "POST") },
                    )

                val result = verifier.verify("DELETE")

                val expected =
                    VerificationResult.Denied(
                        HttpStatusCode.MethodNotAllowed,
                        "Method Not Allowed by Palisade perimeter shield.",
                    )
                assertEquals(expected, result)
            }
    }
}
