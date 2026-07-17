package com.palisade.config

import io.ktor.server.config.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GuardConfigTest {
    @Test
    fun `test guard config parsing with paths and segments`() {
        // 1. Arrange: Mock the environment configuration structure using MapApplicationConfig
        val mockConfig =
            MapApplicationConfig().apply {
                put("palisade.targetHost", "http://localhost:9999")
                put("palisade.verifierParams.maxHeaderSize", "8192")
                put("palisade.verifierParams.maxHeaderCount", "100")
                put("palisade.verifierParams.maxQueryParamCount", "20")
                put("palisade.verifierParams.maxQueryParamValueLength", "100")

                // Deliberately messy strings to thoroughly test cleaning, trimming, and slash-appending
                put("palisade.verifierParams.allowedPathPrefixes", " /v1/ , /palisade-test-loopback , /api/v2 ")
                put("palisade.verifierParams.forbiddenPathSegments", "admin, ,, system, bin ")
            }

        // 2. Act: Execute the config initialization pipeline
        val config = PalisadeConfig.load(mockConfig)

        // 3. Assert: Verify the string manipulation routines formatted outputs cleanly
        assertNotNull(config)
        assertEquals(8192, config.verifierParams.maxHeaderSize)
        assertEquals(100, config.verifierParams.maxHeaderCount)

        // Validate allowedPathPrefixes (Must be trimmed, non-blank, and have trailing slashes)
        val expectedPrefixes = arrayOf("/v1/", "/palisade-test-loopback/", "/api/v2/")
        assertArrayEquals(expectedPrefixes, config.verifierParams.allowedPathPrefixes)

        // Validate forbiddenPathSegments (Must be trimmed and non-blank, no trailing slashes)
        val expectedSegments = arrayOf("admin", "system", "bin")
        assertArrayEquals(expectedSegments, config.verifierParams.forbiddenPathSegments)
    }

    @Test
    fun `test guard config fallback behaviors for missing parameters`() {
        // 1. Arrange: Empty config structure to trigger fallbacks
        val emptyMockConfig =
            MapApplicationConfig().apply {
                put("palisade.targetHost", "http://localhost:9999")
            }

        // 2. Act
        val config = PalisadeConfig.load(emptyMockConfig)

        // 3. Assert: Confirm values safely drop down to structural baselines
        assertEquals(4096, config.verifierParams.maxHeaderSize)
        assertEquals(0, config.verifierParams.maxHeaderCount)
        assertTrue(config.verifierParams.allowedPathPrefixes.isEmpty())
        assertTrue(config.verifierParams.forbiddenPathSegments.isEmpty())
    }

    @Test
    fun `test parsing deduplicates methods and discards typos`() {
        val mockConfig =
            MapApplicationConfig().apply {
                put("palisade.targetHost", "http://localhost:9999")
                put("palisade.allowedMethods", "GET, post, GET, INVALID_VERB, PUT")
            }

        val config = PalisadeConfig.load(mockConfig)
        val resultMethods = config.allowedMethods

        assertEquals(3, resultMethods.size)
        assertTrue(resultMethods.containsAll(listOf("GET", "POST", "PUT")))
        assertFalse(resultMethods.contains("INVALID_VERB"))
    }

    @Test
    fun `test completely invalid configuration falls back to safe defaults`() {
        val mockConfig =
            MapApplicationConfig().apply {
                put("palisade.targetHost", "http://localhost:9999")
                put("palisade.allowedMethods", "BADMETHOD, TYPOHERE")
            }

        val config = PalisadeConfig.load(mockConfig)
        val resultMethods = config.allowedMethods

        assertEquals(2, resultMethods.size)
        assertTrue(resultMethods.containsAll(listOf("GET", "HEAD")))
    }
}
