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
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.sockjs.handler.SessionHandler.Event;
import io.netty.handler.codec.sockjs.util.Callbacks;
import io.netty.util.AttributeKey;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.util.ReferenceCountUtil.release;

/**
 * JSON Padding (JSONP) Polling is a transport where there is no open connection between
 * the client and the server. Instead the client will issue a new request for polling from
 * and sending data to the SockJS service.
 *
 * This handler is responsible for sending data back to the client. Since JSONP is in use
 * it need to inspect the HTTP request to find the callback method which is identified as
 * a query parameter 'c'. The name of the callback method will be used to wrap the data
 * into a javascript function call which is what will returned to the client.
 *
 * @see JsonpSendTransport
 */
public class JsonpPollingTransportInbound extends ChannelInboundHandlerAdapter {

    static final AttributeKey<String> CALLBACK = AttributeKey.valueOf(JsonpPollingTransportInbound.class, "callback");

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            final HttpRequest request = (HttpRequest) msg;
            final String callbackParam = Callbacks.parse(request);
            if (Callbacks.invalid(callbackParam)) {
                release(msg);
                internalServerError(ctx, request, Callbacks.errorMsg(callbackParam));
                ctx.fireUserEventTriggered(Event.CLOSE_SESSION);
                return;
            } else {
                ctx.channel().attr(CALLBACK).set(callbackParam);
            }
            ctx.fireChannelRead(msg);
        }
    }

    private static void internalServerError(final ChannelHandlerContext ctx,
                                            final HttpRequest request,
                                            final String message) {
        ctx.writeAndFlush(HttpResponseBuilder.responseFor(request)
                .status(INTERNAL_SERVER_ERROR)
                .content(message)
                .contentType(HttpResponseBuilder.CONTENT_TYPE_JAVASCRIPT)
                .buildFullResponse());
    }

}

