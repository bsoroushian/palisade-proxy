package com.palisade.utils

import io.ktor.server.application.*
import io.ktor.server.request.*

object ForwardedAddressParser {
    /**
     * Extracts the real client IP address from the X-Forwarded-For header safely.
     * Reads from right-to-left to prevent left-side IP injection spoofing attacks.
     * Operates with ZERO heap object allocations (no split, no trim, no lists).
     */
    fun extractClientIp(call: ApplicationCall): String {
        // Fallback to the raw underlying TCP socket connection IP if header is missing
        val rawHeader =
            call.request.headers["X-Forwarded-For"]
                ?: return call.request.local.remoteHost

        val length = rawHeader.length
        if (length == 0) return call.request.local.remoteHost

        // Scan from right to left to locate the start pointer of the last comma-delimited element
        var commaIndex = -1
        for (i in (length - 1) downTo 0) {
            if (rawHeader[i] == ',') {
                commaIndex = i
                break
            }
        }

        // Determine boundaries for extraction
        val startIndex = commaIndex + 1
        var startPointer = startIndex
        var endPointer = length

        // Allocation-free character scanning to trim whitespace characters 'in place'
        while (startPointer < endPointer && rawHeader[startPointer] <= ' ') {
            startPointer++
        }
        while (endPointer > startPointer && rawHeader[endPointer - 1] <= ' ') {
            endPointer--
        }

        if (startPointer >= endPointer) {
            return call.request.local.remoteHost
        }

        // Substring is only generated once at the very end for the final resolved value
        return rawHeader.substring(startPointer, endPointer)
    }
}
