/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.example.http2.client;

import static io.netty.util.internal.logging.InternalLogLevel.INFO;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http2.draft10.connection.Http2ConnectionHandler;
import io.netty.handler.codec.http2.draft10.frame.Http2FrameCodec;

/**
 * Configures the client pipeline to support HTTP/2 frames.
 */
public class Http2ClientInitializer extends ChannelInitializer<SocketChannel> {

    private final Http2ResponseClientHandler httpResponseHandler;

    public Http2ClientInitializer(Http2ResponseClientHandler httpResponseHandler) {
        this.httpResponseHandler = httpResponseHandler;
    }

    @Override
    public void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();

        pipeline.addLast("http2FrameCodec", new Http2FrameCodec());
        pipeline.addLast("spdyFrameLogger", new Http2FrameLogger(INFO));
        pipeline.addLast("http2ConnectionHandler", new Http2ConnectionHandler(false));
        pipeline.addLast("httpHandler", httpResponseHandler);
    }
}
