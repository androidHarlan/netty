/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.channel.socket.oio;

import io.netty.channel.Channel;
import io.netty.channel.ChannelBufferHolder;
import io.netty.channel.ChannelPipeline;

import java.io.IOException;
import java.util.Queue;

abstract class AbstractOioMessageChannel extends AbstractOioChannel {

    protected AbstractOioMessageChannel(
            Channel parent, Integer id, ChannelBufferHolder<?> outboundBuffer) {
        super(parent, id, outboundBuffer);
    }

    @Override
    protected Unsafe newUnsafe() {
        return new OioMessageUnsafe();
    }

    private class OioMessageUnsafe extends AbstractOioUnsafe {
        @Override
        public void read() {
            assert eventLoop().inEventLoop();

            final ChannelPipeline pipeline = pipeline();
            final ChannelBufferHolder<Object> buf = pipeline.inbound();
            boolean closed = false;
            boolean read = false;
            try {
                Queue<Object> msgBuf = buf.messageBuffer();
                int localReadAmount = doReadMessages(msgBuf);
                if (localReadAmount > 0) {
                    read = true;
                } else if (localReadAmount < 0) {
                    closed = true;
                }
            } catch (Throwable t) {
                if (read) {
                    read = false;
                    pipeline.fireInboundBufferUpdated();
                }
                pipeline().fireExceptionCaught(t);
                if (t instanceof IOException) {
                    close(voidFuture());
                }
            } finally {
                if (read) {
                    pipeline.fireInboundBufferUpdated();
                }
                if (closed && isOpen()) {
                    close(voidFuture());
                }
            }
        }
    }

    @Override
    protected void doFlush(ChannelBufferHolder<Object> buf) throws Exception {
        flushMessageBuf(buf.messageBuffer());
    }

    private void flushMessageBuf(Queue<Object> buf) throws Exception {
        while (!buf.isEmpty()) {
            doWriteMessages(buf);
        }
    }

    protected abstract int doReadMessages(Queue<Object> buf) throws Exception;
    protected abstract int doWriteMessages(Queue<Object> buf) throws Exception;
}
