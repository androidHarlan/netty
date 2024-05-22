/*
 * Copyright 2012 The Netty Project
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
package io.netty5.channel.local;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.util.Resource;
import io.netty5.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LocalTransportThreadModelTest2 {
    static final int messageCountPerRun = 4;

    @Test
    @Timeout(value = 15000, unit = TimeUnit.MILLISECONDS)
    public void testSocketReuse() throws Exception {
        LocalAddress address = new LocalAddress(LocalTransportThreadModelTest2.class);
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        LocalHandler serverHandler = new LocalHandler("SERVER");
        serverBootstrap
                .group(new MultithreadEventLoopGroup(LocalIoHandler.newFactory()),
                        new MultithreadEventLoopGroup(LocalIoHandler.newFactory()))
                .channel(LocalServerChannel.class)
                .childHandler(serverHandler);

        Bootstrap clientBootstrap = new Bootstrap();
        LocalHandler clientHandler = new LocalHandler("CLIENT");
        clientBootstrap
                .group(new MultithreadEventLoopGroup(LocalIoHandler.newFactory()))
                .channel(LocalChannel.class)
                .remoteAddress(address).handler(clientHandler);

        serverBootstrap.bind(address).asStage().sync();

        int count = 100;
        for (int i = 1; i < count + 1; i ++) {
            Channel ch = clientBootstrap.connect().asStage().get();

            // SPIN until we get what we are looking for.
            int target = i * messageCountPerRun;
            while (serverHandler.count.get() != target || clientHandler.count.get() != target) {
                Thread.sleep(50);
            }
            close(ch, clientHandler);
        }

        assertEquals(count * 2 * messageCountPerRun, serverHandler.count.get() +
                clientHandler.count.get());
    }

    public void close(final Channel localChannel, final LocalHandler localRegistrationHandler) throws Exception {
        // we want to make sure we actually shutdown IN the event loop
        if (localChannel.executor().inEventLoop()) {
            // Wait until all messages are flushed before closing the channel.
            if (localRegistrationHandler.lastWriteFuture != null) {
                localRegistrationHandler.lastWriteFuture.asStage().sync();
            }

            localChannel.close();
            return;
        }

        localChannel.executor().submit(() -> {
            close(localChannel, localRegistrationHandler);
            return null;
        });

        // Wait until the connection is closed or the connection attempt fails.
        localChannel.closeFuture().asStage().await();
    }

    static class LocalHandler implements ChannelHandler {
        private final String name;

        public volatile Future<Void> lastWriteFuture;

        public final AtomicInteger count = new AtomicInteger(0);

        LocalHandler(String name) {
            this.name = name;
        }

        @Override
        public boolean isSharable() {
            return true;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            for (int i = 0; i < messageCountPerRun; i ++) {
                lastWriteFuture = ctx.channel().write(name + ' ' + i);
            }
            ctx.channel().flush();
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            count.incrementAndGet();
            Resource.dispose(msg);
        }
    }
}
