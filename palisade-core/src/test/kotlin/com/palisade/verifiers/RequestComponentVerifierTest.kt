package com.palisade.verifiers

import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Nested
import kotlin.test.*

class RequestComponentVerifierTest {
    @Nested
    inner class ForbiddenPathSegmentsTests {
        private val verifier = ForbiddenPathSegmentsVerifier(forbiddenSegmentsProvider = { arrayOf("/../", "//") })

        @Test
        fun `should return 'BadRequest' and block requests that have forbidden segment in path`() =
            runBlocking {
                val walkBackResult = verifier.verify("", "/some-path/b/../c")
                assertIs<VerificationResult.Denied>(walkBackResult)
                assertEquals(HttpStatusCode.BadRequest, walkBackResult.status)
                assertEquals("Malicious request path structure detected", walkBackResult.message)

                val doubleSlashResult = verifier.verify("", "/some-path//some-other-segment/backend")
                assertIs<VerificationResult.Denied>(doubleSlashResult)
                assertEquals(HttpStatusCode.BadRequest, doubleSlashResult.status)
                assertEquals("Malicious request path structure detected", doubleSlashResult.message)
            }

        @Test
        fun `should safely allow requests that don't include any forbidden segment`() =
            runBlocking {
                val result = verifier.verify("", "/some-path/api/v1/")

                assertIs<VerificationResult.Allowed>(result)
            }
    }

    @Nested
    inner class AllowedPathPrefixTests {
        @Test
        fun `an empty list of allowed path prefixes disable the rule`() =
            runBlocking {
                val verifier = AllowedPathPrefixVerifier(allowedPrefixesProvider = { emptyArray() })

                val result = verifier.verify("", "/any-arbitraryPath/record")
                assertIs<VerificationResult.Allowed>(result)
            }

        @Test
        fun `should allow requests where path matches allowed path prefixes`() =
            runBlocking {
                val verifier = AllowedPathPrefixVerifier(allowedPrefixesProvider = { arrayOf("/api/v1/", "/static/") })

                val result = verifier.verify("", "/api/v1/users")
                assertIs<VerificationResult.Allowed>(result)
            }

        @Test
        fun `should correctly deny requests that try to smuggle path without trailing slash`() =
            runBlocking {
                val verifier = AllowedPathPrefixVerifier(allowedPrefixesProvider = { arrayOf("/api/v1/", "/static/") })

                val result = verifier.verify("", "/api/v1")
                assertIs<VerificationResult.Allowed>(result)
            }
    }

    @Nested
    inner class MaxQueryParamCountTests {
        private val verifier = MaxQueryParamCountVerifier(maxQueryParamCountProvider = { 3 })

        @Test
        fun `should allow requests without any query parameters`() =
            runBlocking {
                val result = verifier.verify("/api/v1/user", "")

                assertIs<VerificationResult.Allowed>(result)
            }

        @Test
        fun `should allow requests with acceptable number of query parameters`() =
            runBlocking {
                val result = verifier.verify("/search?a=1&b=2&c=3", "")

                assertIs<VerificationResult.Allowed>(result)
            }

        @Test
        fun `should deny requests for which number of query parameters exceed the limit`() =
            runBlocking {
                val result = verifier.verify("/search?a=1&b=2&c=3&d=4", "")

                assertIs<VerificationResult.Denied>(result)
                assertEquals(HttpStatusCode.BadRequest, result.status)
                assertEquals("The number of query parameters exceeds the allowable limit configured by Palisade.", result.message)
            }

        @Test
        fun `should correctly deny a requests with trailing ampersands exceeding the limit`() =
            runBlocking {
                val result = verifier.verify("/search?&&&&", "")

                assertIs<VerificationResult.Denied>(result)
                assertEquals(HttpStatusCode.BadRequest, result.status)
                assertEquals("The number of query parameters exceeds the allowable limit configured by Palisade.", result.message)
            }
    }

    @Nested
    inner class QueryParameterLengthTests {
        private val verifier = QueryParameterLengthVerifier(maxParamLengthProvider = { 5 })

        @Test
        fun `should allow requests without query parameters or with an empty question mark`() =
            runBlocking {
                val result = verifier.verify("/api/v1/users", "")

                assertIs<VerificationResult.Allowed>(result)

                val resultWithEmptyQuestionMark = verifier.verify("/api/v1/users?", "")

                assertIs<VerificationResult.Allowed>(resultWithEmptyQuestionMark)
            }

        @Test
        fun `should allow requests with query parameters whose size does not exceed limit`() =
            runBlocking {
                val result = verifier.verify("/search?key=123", "")
                assertIs<VerificationResult.Allowed>(result)
            }

        @Test
        fun `should deny requests where query parameter key has a length exceeding the limit`() =
            runBlocking {
                val result = verifier.verify("search?verylongkeyname=1", "")
                assertIs<VerificationResult.Denied>(result)
                assertEquals(HttpStatusCode.BadRequest, result.status)
                assertEquals("An individual query key or value exceeds the allowable limit configured by Palisade.", result.message)
            }

        @Test
        fun `should deny requests where query parameter value has a length exceeding the limit`() =
            runBlocking {
                val result = verifier.verify("/search?key=thisvalueiswaytoolong", "")
                assertIs<VerificationResult.Denied>(result)
                assertEquals(HttpStatusCode.BadRequest, result.status)
                assertEquals("An individual query key or value exceeds the allowable limit configured by Palisade.", result.message)
            }
    }
}
