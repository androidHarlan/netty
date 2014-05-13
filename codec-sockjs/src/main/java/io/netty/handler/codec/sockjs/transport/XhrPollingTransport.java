/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.sockjs.transport;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.sockjs.SockJsConfig;
import io.netty.handler.codec.sockjs.protocol.Frame;
import io.netty.handler.codec.sockjs.util.ArgumentUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpHeaders.Values.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.sockjs.transport.Transports.*;

/**
 * XMLHttpRequest (XHR) Polling is a transport where there is no open connection between
 * the client and the server. Instead the client will issue a new request for polling from
 * and sending data to the SockJS service.
 *
 * This handler is responsible for handling {@link Frame}s and sending the
 * content back to the client. These frames are generated by the SockJS session handling.
 *
 * @see XhrSendTransport
 */
public class XhrPollingTransport extends ChannelHandlerAdapter {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(XhrPollingTransport.class);

    private final SockJsConfig config;
    private final FullHttpRequest request;

    /**
     * Sole constructor.
     *
     * @param config the SockJS {@link SockJsConfig} instance.
     * @param request the {@link FullHttpRequest} which can be used get information like the HTTP version.
     */
    public XhrPollingTransport(final SockJsConfig config, final FullHttpRequest request) {
        ArgumentUtil.checkNotNull(config, "config");
        ArgumentUtil.checkNotNull(request, "request");
        this.config = config;
        this.request = request;
    }

    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise)
            throws Exception {
        if (msg instanceof Frame) {
            final Frame frame = (Frame) msg;
            final ByteBuf content = Transports.wrapWithLN(frame.content());
            frame.release();
            final FullHttpResponse response = new DefaultFullHttpResponse(request.getProtocolVersion(), OK, content);
            response.headers().set(CONTENT_TYPE, CONTENT_TYPE_JAVASCRIPT);
            response.headers().set(CONTENT_LENGTH, content.readableBytes());
            response.headers().set(CONNECTION, CLOSE);
            Transports.setDefaultHeaders(response, config, request);
            Transports.writeResponse(ctx, promise, response);
        } else {
            ctx.writeAndFlush(ReferenceCountUtil.retain(msg), promise);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("caught exception : ", cause);
        ctx.fireExceptionCaught(cause);
    }

}

