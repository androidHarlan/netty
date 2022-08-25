/*
 * Copyright 2020 The Netty Project
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
package io.netty5.handler.codec.http2;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.nio.NioHandler;
import io.netty5.channel.socket.SocketChannel;
import io.netty5.channel.socket.nio.NioServerSocketChannel;
import io.netty5.channel.socket.nio.NioSocketChannel;
import io.netty5.handler.codec.http2.headers.Http2Headers;
import io.netty5.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import static io.netty5.handler.codec.http2.Http2TestUtil.bb;
import static io.netty5.util.internal.SilentDispose.autoClosing;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DefaultHttp2PushPromiseFrameTest {

    private final EventLoopGroup eventLoopGroup = new MultithreadEventLoopGroup(2, NioHandler.newFactory());
    private final ClientHandler clientHandler = new ClientHandler();
    private final Map<Integer, String> contentMap = new ConcurrentHashMap<Integer, String>();

    private Future<Channel> connectionFuture;

    @BeforeEach
    public void setup() throws Exception {
        ServerBootstrap serverBootstrap = new ServerBootstrap()
                .group(eventLoopGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();

                        Http2FrameCodec frameCodec = Http2FrameCodecBuilder.forServer()
                                .autoAckSettingsFrame(true)
                                .autoAckPingFrame(true)
                                .build();

                        pipeline.addLast(frameCodec);
                        pipeline.addLast(new ServerHandler());
                    }
                });

        Channel channel = serverBootstrap.bind(0).asStage().get();

        final Bootstrap bootstrap = new Bootstrap()
                .group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();

                        Http2FrameCodec frameCodec = Http2FrameCodecBuilder.forClient()
                                .autoAckSettingsFrame(true)
                                .autoAckPingFrame(true)
                                .initialSettings(Http2Settings.defaultSettings().pushEnabled(true))
                                .build();

                        pipeline.addLast(frameCodec);
                        pipeline.addLast(clientHandler);
                    }
                });

        connectionFuture = bootstrap.connect(channel.localAddress());
    }

    @Test
    public void send() {
        connectionFuture.addListener(future -> clientHandler.write());
    }

    @AfterEach
    public void shutdown() {
        eventLoopGroup.shutdownGracefully();
    }

    private final class ServerHandler extends Http2ChannelDuplexHandler {

        @Override
        public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {

            if (msg instanceof Http2HeadersFrame) {
                final Http2HeadersFrame receivedFrame = (Http2HeadersFrame) msg;

                Http2Headers pushRequestHeaders = Http2Headers.newHeaders();
                pushRequestHeaders.path("/meow")
                        .method("GET")
                        .scheme("https")
                        .authority("localhost:5555");

                // Write PUSH_PROMISE request headers
                final Http2FrameStream newPushFrameStream = newStream();
                Http2PushPromiseFrame pushPromiseFrame = new DefaultHttp2PushPromiseFrame(pushRequestHeaders);
                pushPromiseFrame.stream(receivedFrame.stream());
                pushPromiseFrame.pushStream(newPushFrameStream);
                ctx.writeAndFlush(pushPromiseFrame).addListener(future -> {
                    contentMap.put(newPushFrameStream.id(), "Meow, I am Pushed via HTTP/2");

                    // Write headers for actual request
                    Http2Headers http2Headers = Http2Headers.newHeaders();
                    http2Headers.status("200");
                    http2Headers.add("push", "false");
                    Http2HeadersFrame headersFrame = new DefaultHttp2HeadersFrame(http2Headers, false);
                    headersFrame.stream(receivedFrame.stream());
                    Future<Void> channelFuture = ctx.writeAndFlush(headersFrame);

                    // Write Data of actual request
                    channelFuture.addListener(fut -> {
                        Http2DataFrame dataFrame = new DefaultHttp2DataFrame(bb("Meow").send(), true);
                        dataFrame.stream(receivedFrame.stream());
                        ctx.writeAndFlush(dataFrame);
                    });
                });
            } else if (msg instanceof Http2PriorityFrame) {
                Http2PriorityFrame priorityFrame = (Http2PriorityFrame) msg;
                String content = contentMap.get(priorityFrame.stream().id());
                if (content == null) {
                    ctx.writeAndFlush(new DefaultHttp2GoAwayFrame(Http2Error.REFUSED_STREAM));
                    return;
                }

                // Write headers for Priority request
                Http2Headers http2Headers = Http2Headers.newHeaders();
                http2Headers.status("200");
                http2Headers.add("push", "true");
                Http2HeadersFrame headersFrame = new DefaultHttp2HeadersFrame(http2Headers, false);
                headersFrame.stream(priorityFrame.stream());
                ctx.writeAndFlush(headersFrame);

                // Write Data of Priority request
                Http2DataFrame dataFrame = new DefaultHttp2DataFrame(bb(content).send(), true);
                dataFrame.stream(priorityFrame.stream());
                ctx.writeAndFlush(dataFrame);
            }
        }
    }

    private static final class ClientHandler extends Http2ChannelDuplexHandler {

        private final CountDownLatch latch = new CountDownLatch(1);
        private volatile ChannelHandlerContext ctx;

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws InterruptedException {
            this.ctx = ctx;
            latch.countDown();
        }

        void write() throws InterruptedException {
            latch.await();
            Http2Headers http2Headers = Http2Headers.newHeaders();
            http2Headers.path("/")
                    .authority("localhost")
                    .method("GET")
                    .scheme("https");

            Http2HeadersFrame headersFrame = new DefaultHttp2HeadersFrame(http2Headers, true);
            headersFrame.stream(newStream());
            ctx.writeAndFlush(headersFrame);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof Http2PushPromiseFrame) {
                Http2PushPromiseFrame pushPromiseFrame = (Http2PushPromiseFrame) msg;

                assertEquals("/meow", pushPromiseFrame.http2Headers().path().toString());
                assertEquals("GET", pushPromiseFrame.http2Headers().method().toString());
                assertEquals("https", pushPromiseFrame.http2Headers().scheme().toString());
                assertEquals("localhost:5555", pushPromiseFrame.http2Headers().authority().toString());

                Http2PriorityFrame priorityFrame = new DefaultHttp2PriorityFrame(pushPromiseFrame.stream().id(),
                        Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT, true);
                priorityFrame.stream(pushPromiseFrame.pushStream());
                ctx.writeAndFlush(priorityFrame);
            } else if (msg instanceof Http2HeadersFrame) {
                Http2HeadersFrame headersFrame = (Http2HeadersFrame) msg;

                if (headersFrame.stream().id() == 3) {
                    assertEquals("200", headersFrame.headers().status().toString());
                    assertEquals("false", headersFrame.headers().get("push").toString());
                } else if (headersFrame.stream().id() == 2) {
                    assertEquals("200", headersFrame.headers().status().toString());
                    assertEquals("true", headersFrame.headers().get("push").toString());
                } else {
                    ctx.writeAndFlush(new DefaultHttp2GoAwayFrame(Http2Error.REFUSED_STREAM));
                }
            } else if (msg instanceof Http2DataFrame) {
                Http2DataFrame dataFrame = (Http2DataFrame) msg;

                try (AutoCloseable ignore = autoClosing(dataFrame)) {
                    if (dataFrame.stream().id() == 3) {
                        assertEquals("Meow", dataFrame.content().toString(UTF_8));
                    } else if (dataFrame.stream().id() == 2) {
                        assertEquals("Meow, I am Pushed via HTTP/2", dataFrame.content().toString(UTF_8));
                    } else {
                        ctx.writeAndFlush(new DefaultHttp2GoAwayFrame(Http2Error.REFUSED_STREAM));
                    }
                }
            }
        }
    }
}
