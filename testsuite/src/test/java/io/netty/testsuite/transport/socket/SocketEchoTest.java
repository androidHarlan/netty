/*
 * Copyright 2012 The Netty Project
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
package io.netty.testsuite.transport.socket;

import static org.junit.Assert.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundByteHandlerAdapter;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

public class SocketEchoTest extends AbstractSocketTest {

    private static final Random random = new Random();
    static final byte[] data = new byte[1048576];

    static {
        random.nextBytes(data);
    }

    @Test
    public void testSimpleEcho() throws Throwable {
        run();
    }

    public void testSimpleEcho(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testSimpleEcho0(sb, cb, Integer.MAX_VALUE);
    }

    @Test
    public void testSimpleEchoWithBoundedBuffer() throws Throwable {
        run();
    }

    public void testSimpleEchoWithBoundedBuffer(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testSimpleEcho0(sb, cb, 32);
    }

    private static void testSimpleEcho0(ServerBootstrap sb, Bootstrap cb, int maxInboundBufferSize) throws Throwable {
        EchoHandler sh = new EchoHandler(maxInboundBufferSize);
        EchoHandler ch = new EchoHandler(maxInboundBufferSize);

        sb.childHandler(sh);
        cb.handler(ch);

        Channel sc = sb.bind().sync().channel();
        Channel cc = cb.connect().sync().channel();

        for (int i = 0; i < data.length;) {
            int length = Math.min(random.nextInt(1024 * 64), data.length - i);
            cc.write(Unpooled.wrappedBuffer(data, i, length));
            i += length;
        }

        while (ch.counter < data.length) {
            if (sh.exception.get() != null) {
                break;
            }
            if (ch.exception.get() != null) {
                break;
            }

            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                // Ignore.
            }
        }

        while (sh.counter < data.length) {
            if (sh.exception.get() != null) {
                break;
            }
            if (ch.exception.get() != null) {
                break;
            }

            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                // Ignore.
            }
        }

        sh.channel.close().sync();
        ch.channel.close().sync();
        sc.close().sync();

        if (sh.exception.get() != null && !(sh.exception.get() instanceof IOException)) {
            throw sh.exception.get();
        }
        if (ch.exception.get() != null && !(ch.exception.get() instanceof IOException)) {
            throw ch.exception.get();
        }
        if (sh.exception.get() != null) {
            throw sh.exception.get();
        }
        if (ch.exception.get() != null) {
            throw ch.exception.get();
        }
    }

    private static class EchoHandler extends ChannelInboundByteHandlerAdapter {
        private final int maxInboundBufferSize;
        volatile Channel channel;
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        volatile int counter;

        EchoHandler(int maxInboundBufferSize) {
            this.maxInboundBufferSize = maxInboundBufferSize;
        }

        @Override
        public ByteBuf newInboundBuffer(ChannelHandlerContext ctx) throws Exception {
            return Unpooled.buffer(0, maxInboundBufferSize);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx)
                throws Exception {
            channel = ctx.channel();
        }

        @Override
        public void inboundBufferUpdated(
                ChannelHandlerContext ctx, ByteBuf in)
                throws Exception {
            byte[] actual = new byte[in.readableBytes()];
            in.readBytes(actual);

            int lastIdx = counter;
            for (int i = 0; i < actual.length; i ++) {
                assertEquals(data[i + lastIdx], actual[i]);
            }

            if (channel.parent() != null) {
                channel.write(Unpooled.wrappedBuffer(actual));
            }

            counter += actual.length;
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx,
                Throwable cause) throws Exception {
            if (exception.compareAndSet(null, cause)) {
                ctx.close();
            }
        }
    }
}
