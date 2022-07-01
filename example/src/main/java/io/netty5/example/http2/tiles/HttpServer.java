/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty5.example.http2.tiles;

import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.socket.SocketChannel;
import io.netty5.channel.socket.nio.NioServerSocketChannel;
import io.netty5.handler.codec.http.HttpObjectAggregator;
import io.netty5.handler.codec.http.HttpRequestDecoder;
import io.netty5.handler.codec.http.HttpResponseEncoder;
import io.netty5.handler.logging.LogLevel;
import io.netty5.handler.logging.LoggingHandler;
import io.netty5.util.concurrent.Future;

/**
 * Demonstrates an http server using Netty to display a bunch of images, simulate
 * latency and compare it against the http2 implementation.
 */
public final class HttpServer {

    public static final int PORT = Integer.parseInt(System.getProperty("http-port", "8080"));
    private static final int MAX_CONTENT_LENGTH = 1024 * 100;

    private final EventLoopGroup group;

    public HttpServer(EventLoopGroup eventLoopGroup) {
        group = eventLoopGroup;
    }

    public Future<Void> start() throws Exception {
        ServerBootstrap b = new ServerBootstrap();
        b.option(ChannelOption.SO_BACKLOG, 1024);

        b.group(group).channel(NioServerSocketChannel.class).handler(new LoggingHandler(LogLevel.INFO))
        .childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(new HttpRequestDecoder(),
                                      new HttpResponseEncoder(),
                                      new HttpObjectAggregator(MAX_CONTENT_LENGTH),
                                      new Http1RequestHandler());
            }
        });

        Channel ch = b.bind(PORT).asStage().get();
        return ch.closeFuture();
    }
}
