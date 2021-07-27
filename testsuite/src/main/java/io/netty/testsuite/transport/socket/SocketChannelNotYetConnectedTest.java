/*
 * Copyright 2016 The Netty Project
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
package io.netty.testsuite.transport.socket;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SingleThreadEventLoop;
import io.netty.channel.nio.NioHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import java.net.SocketException;
import java.nio.channels.NotYetConnectedException;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

public class SocketChannelNotYetConnectedTest extends AbstractClientSocketTest {
    @Test
    @Timeout(30)
    public void testShutdownNotYetConnected(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testShutdownNotYetConnected);
    }

    public void testShutdownNotYetConnected(Bootstrap cb) throws Throwable {
        SocketChannel ch = (SocketChannel) cb.handler(new ChannelHandler() { })
                .bind(newSocketAddress()).get();
        try {
            try {
                ch.shutdownInput().syncUninterruptibly();
                fail();
            } catch (Throwable cause) {
                checkThrowable(cause.getCause());
            }

            try {
                ch.shutdownOutput().syncUninterruptibly();
                fail();
            } catch (Throwable cause) {
                checkThrowable(cause.getCause());
            }
        } finally {
            ch.close().syncUninterruptibly();
        }
    }

    private static void checkThrowable(Throwable cause) throws Throwable {
        // Depending on OIO / NIO both are ok
        if (!(cause instanceof NotYetConnectedException) && !(cause instanceof SocketException)) {
            throw cause;
        }
    }

    @Test
    @Timeout(30)
    public void readMustBePendingUntilChannelIsActive(TestInfo info) throws Throwable {
        run(info, new Runner<Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap) throws Throwable {
                SingleThreadEventLoop group = new SingleThreadEventLoop(
                        new DefaultThreadFactory(getClass()), NioHandler.newFactory().newHandler());
                ServerBootstrap sb = new ServerBootstrap().group(group);
                Channel serverChannel = sb.childHandler(new ChannelHandlerAdapter() {
                    @Override
                    public void channelActive(ChannelHandlerContext ctx) throws Exception {
                        ctx.writeAndFlush(Unpooled.copyInt(42));
                    }
                }).channel(NioServerSocketChannel.class).bind(0).get();

                final CountDownLatch readLatch = new CountDownLatch(1);
                bootstrap.handler(new ByteToMessageDecoder() {
                    @Override
                    public void handlerAdded0(ChannelHandlerContext ctx) throws Exception {
                        assertFalse(ctx.channel().isActive());
                        ctx.read();
                    }

                    @Override
                    protected void decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
                        assertThat(in.readableBytes()).isLessThanOrEqualTo(Integer.BYTES);
                        if (in.readableBytes() == Integer.BYTES) {
                            assertThat(in.readInt()).isEqualTo(42);
                            readLatch.countDown();
                        }
                    }
                });
                bootstrap.connect(serverChannel.localAddress()).sync();

                readLatch.await();
                group.shutdownGracefully().await();
            }
        });
    }
}
