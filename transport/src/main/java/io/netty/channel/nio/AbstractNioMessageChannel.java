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
package io.netty.channel.nio;

import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.channel.ServerChannel;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link AbstractNioChannel} base class for {@link Channel}s that operate on messages.
 */
public abstract class AbstractNioMessageChannel extends AbstractNioChannel {

    protected AbstractNioMessageChannel(
            Channel parent, EventLoop eventLoop, SelectableChannel ch, int readInterestOp) {
        super(parent, eventLoop, ch, readInterestOp);
    }

    @Override
    protected AbstractNioUnsafe newUnsafe() {
        return new NioMessageUnsafe();
    }

    private final class NioMessageUnsafe extends AbstractNioUnsafe {

        private final List<Object> readBuf = new ArrayList<Object>();

        private void removeReadOp() {
            SelectionKey key = selectionKey();
            int interestOps = key.interestOps();
            if ((interestOps & readInterestOp) != 0) {
                // only remove readInterestOp if needed
                key.interestOps(interestOps & ~readInterestOp);
            }
        }
        @Override
        public void read() {
            assert eventLoop().inEventLoop();
            final ChannelConfig config = config();

            try {
                final int maxMessagesPerRead = config.getMaxMessagesPerRead();
                final ChannelPipeline pipeline = pipeline();
                boolean closed = false;
                Throwable exception = null;
                try {
                    for (;;) {
                        int localRead = doReadMessages(readBuf);
                        if (localRead == 0) {
                            break;
                        }
                        if (localRead < 0) {
                            closed = true;
                            break;
                        }

                        // stop reading and remove op
                        if (!config.isAutoRead()) {
                            break;
                        }

                        if (readBuf.size() >= maxMessagesPerRead) {
                            break;
                        }
                    }
                } catch (Throwable t) {
                    exception = t;
                }

                int size = readBuf.size();
                for (int i = 0; i < size; i ++) {
                    pipeline.fireChannelRead(readBuf.get(i));
                }
                readBuf.clear();
                pipeline.fireChannelReadComplete();

                if (exception != null) {
                    if (exception instanceof IOException) {
                        // ServerChannel should not be closed even on IOException because it can often continue
                        // accepting incoming connections. (e.g. too many open files)
                        closed = !(AbstractNioMessageChannel.this instanceof ServerChannel);
                    }

                    pipeline.fireExceptionCaught(exception);
                }

                if (closed) {
                    if (isOpen()) {
                        close(voidPromise());
                    }
                }
            } finally {
                if (!config().isAutoRead()) {
                    removeReadOp();
                }
            }
        }
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        final SelectionKey key = selectionKey();
        final int interestOps = key.interestOps();

        for (;;) {
            Object msg = in.current();
            if (msg == null) {
                // Wrote all messages.
                if ((interestOps & SelectionKey.OP_WRITE) != 0) {
                    key.interestOps(interestOps & ~SelectionKey.OP_WRITE);
                }
                break;
            }

            boolean done = false;
            for (int i = config().getWriteSpinCount() - 1; i >= 0; i --) {
                if (doWriteMessage(msg, in)) {
                    done = true;
                    break;
                }
            }

            if (done) {
                in.remove();
            } else {
                // Did not write all messages.
                if ((interestOps & SelectionKey.OP_WRITE) == 0) {
                    key.interestOps(interestOps | SelectionKey.OP_WRITE);
                }
                break;
            }
        }
    }

    /**
     * Read messages into the given array and return the amount which was read.
     */
    protected abstract int doReadMessages(List<Object> buf) throws Exception;

    /**
     * Write a message to the underlying {@link java.nio.channels.Channel}.
     *
     * @return {@code true} if and only if the message has been written
     */
    protected abstract boolean doWriteMessage(Object msg, ChannelOutboundBuffer in) throws Exception;

}
