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
package io.netty.channel;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ChannelBufType;
import io.netty.buffer.MessageBuf;

import java.net.SocketAddress;

/**
 * A skeletal server-side {@link Channel} implementation.  A server-side
 * {@link Channel} does not allow the following operations:
 * <ul>
 * <li>{@link #connect(SocketAddress, ChannelFuture)}</li>
 * <li>{@link #disconnect(ChannelFuture)}</li>
 * <li>{@link #flush(ChannelFuture)}</li>
 * <li>and the shortcut methods which calls the methods mentioned above
 * </ul>
 */
public abstract class AbstractServerChannel extends AbstractChannel implements ServerChannel {

    private static final ChannelMetadata METADATA = new ChannelMetadata(ChannelBufType.MESSAGE, false);

    /**
     * Creates a new instance.
     */
    protected AbstractServerChannel(Integer id) {
        super(null, id);
    }

    @Override
    public ByteBuf outboundByteBuffer() {
        throw new NoSuchBufferException(String.format(
                "%s does not have an outbound buffer.", ServerChannel.class.getSimpleName()));
    }

    @Override
    public <T> MessageBuf<T> outboundMessageBuffer() {
        throw new NoSuchBufferException(String.format(
                "%s does not have an outbound buffer.", ServerChannel.class.getSimpleName()));
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    public SocketAddress remoteAddress() {
        return null;
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return null;
    }

    @Override
    protected void doDisconnect() throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void doFlushByteBuffer(ByteBuf buf) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void doFlushMessageBuffer(MessageBuf<Object> buf) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    protected boolean isFlushPending() {
        return false;
    }

    protected abstract class AbstractServerUnsafe extends AbstractUnsafe {

        @Override
        public void flush(final ChannelFuture future) {
            if (eventLoop().inEventLoop()) {
                reject(future);
            } else {
                eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        flush(future);
                    }
                });
            }
        }

        @Override
        public void connect(
                final SocketAddress remoteAddress, final SocketAddress localAddress,
                final ChannelFuture future) {
            if (eventLoop().inEventLoop()) {
                reject(future);
            } else {
                eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        connect(remoteAddress, localAddress, future);
                    }
                });
            }
        }

        private void reject(ChannelFuture future) {
            Exception cause = new UnsupportedOperationException();
            future.setFailure(cause);
            pipeline().fireExceptionCaught(cause);
        }
    }
}
