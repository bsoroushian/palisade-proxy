package com.palisade.verifiers

import io.ktor.http.HttpStatusCode

sealed interface VerificationResult {
    object Allowed : VerificationResult

    data class Denied(val status: HttpStatusCode, val message: String) : VerificationResult
}

interface HeaderVerifier {
    suspend fun verify(
        name: String,
        values: List<String>,
    ): VerificationResult
}

interface MethodVerifier {
    suspend fun verify(method: String): VerificationResult
}

interface RequestComponentVerifier {
    suspend fun verify(
        uri: String,
        path: String,
    ): VerificationResult
}
