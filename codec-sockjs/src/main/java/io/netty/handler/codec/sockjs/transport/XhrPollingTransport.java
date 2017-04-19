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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.sockjs.SockJsConfig;
import io.netty.handler.codec.sockjs.protocol.Frame;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpHeaders.Values.*;
import static io.netty.handler.codec.sockjs.transport.HttpResponseBuilder.*;
import static io.netty.handler.codec.sockjs.util.Arguments.checkNotNull;

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
public class XhrPollingTransport extends ChannelOutboundHandlerAdapter {

    private final SockJsConfig config;
    private final FullHttpRequest request;

    /**
     * Sole constructor.
     *
     * @param config the SockJS {@link SockJsConfig} instance.
     * @param request the {@link FullHttpRequest} which can be used get information like the HTTP version.
     */
    public XhrPollingTransport(final SockJsConfig config, final FullHttpRequest request) {
        checkNotNull(config, "config");
        checkNotNull(request, "request");
        this.config = config;
        this.request = request;
    }

    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise)
            throws Exception {
        if (msg instanceof Frame) {
            final Frame frame = (Frame) msg;
            ctx.write(responseFor(request)
                            .ok()
                            .contentWrappedWithNL(frame.content())
                            .contentType(CONTENT_TYPE_JAVASCRIPT)
                            .setCookie(config)
                            .header(CONNECTION, CLOSE)
                            .header(CACHE_CONTROL, NO_CACHE_HEADER)
                            .buildFullResponse(),
                    promise);
        } else {
            ctx.write(msg, promise);
        }
    }

}

