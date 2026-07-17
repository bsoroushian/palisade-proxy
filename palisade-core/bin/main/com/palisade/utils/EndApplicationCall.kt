package com.palisade.utils

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.netty.NettyApplicationRequest
import io.ktor.server.response.respond
import io.ktor.util.pipeline.PipelineContext
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelFutureListener
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http2.*
import io.netty.util.CharsetUtil

object EndApplicationCall {
    // Note: Marked as suspend to support the non-blocking Ktor call.respond in tests
    suspend fun dropApplicationCall(
        context: PipelineContext<Unit, ApplicationCall>,
        call: ApplicationCall,
        status: HttpStatusCode,
        message: String,
    ) {
        val payload = "$message\n"
        val request = call.request as? NettyApplicationRequest
        val ctx = request?.context

        if (ctx != null) {
            val version = call.request.local.version
            val content = Unpooled.copiedBuffer(payload, CharsetUtil.UTF_8)
            val nettyStatus = HttpResponseStatus.valueOf(status.value)

            if (version.startsWith("HTTP/2")) {
                // 1. Explicitly fetch the modern Http2FrameStream wrapper from the channel attributes
                // This resolves the "Cannot infer type parameter" compilation error.
                val streamChannel = ctx.channel() as? io.netty.handler.codec.http2.Http2StreamChannel
                val frameStream = streamChannel?.stream()

                val headers =
                    DefaultHttp2Headers().apply {
                        status(nettyStatus.codeAsText())
                        set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8")
                        set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes().toString())
                    }

                // 2. Instantiate and bind the frameStream property safely.
                // If frameStream is null (e.g., fallback), Netty handles default serializations.
                val headersFrame =
                    DefaultHttp2HeadersFrame(headers, false).apply {
                        if (frameStream != null) stream(frameStream)
                    }

                val dataFrame =
                    DefaultHttp2DataFrame(content, true).apply {
                        if (frameStream != null) stream(frameStream)
                    }

                ctx.write(headersFrame)
                ctx.write(dataFrame)

                // 3. For an HTTP/2 connection close, send a clean GoAway block to the parent context
                val goAwayFrame = DefaultHttp2GoAwayFrame(Http2Error.NO_ERROR)
                ctx.writeAndFlush(goAwayFrame).addListener(ChannelFutureListener.CLOSE)
            } else {
                // HTTP/1.1 path remains perfectly optimal
                val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, nettyStatus, content)
                response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8")
                response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes())
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)

                ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
            }

            context.finish()
            return
        }

        // 2. UNIT TEST PATH: Evaluated sequentially, preventing coroutine race conditions
        val isTestEnvironment = call.response.javaClass.name == "io.ktor.server.testing.TestApplicationResponse"
        if (isTestEnvironment) {
            call.respond(status, message)
            context.finish()
            return
        }
    }
}
