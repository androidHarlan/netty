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
package io.netty5.channel.socket.nio;

import io.netty5.buffer.api.Buffer;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelException;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.ChannelOutboundBuffer;
import io.netty5.channel.ChannelShutdownDirection;
import io.netty5.channel.EventLoop;
import io.netty5.channel.FileRegion;
import io.netty5.channel.RecvBufferAllocator;
import io.netty5.channel.nio.AbstractNioByteChannel;
import io.netty5.channel.socket.DefaultSocketChannelConfig;
import io.netty5.channel.socket.InternetProtocolFamily;
import io.netty5.channel.socket.SocketChannelConfig;
import io.netty5.util.concurrent.GlobalEventExecutor;
import io.netty5.util.internal.SocketUtils;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Map;
import java.util.concurrent.Executor;

import static io.netty5.channel.internal.ChannelUtils.MAX_BYTES_PER_GATHERING_WRITE_ATTEMPTED_LOW_THRESHOLD;

/**
 * {@link io.netty5.channel.socket.SocketChannel} which uses NIO selector based implementation.
 */
public class NioSocketChannel
        extends AbstractNioByteChannel<NioServerSocketChannel, InetSocketAddress, InetSocketAddress>
        implements io.netty5.channel.socket.SocketChannel {
    private static final SelectorProvider DEFAULT_SELECTOR_PROVIDER = SelectorProvider.provider();

    private static final Method OPEN_SOCKET_CHANNEL_WITH_FAMILY =
            SelectorProviderUtil.findOpenMethod("openSocketChannel");

    private static SocketChannel newChannel(SelectorProvider provider, InternetProtocolFamily family) {
        try {
            SocketChannel channel = SelectorProviderUtil.newChannel(OPEN_SOCKET_CHANNEL_WITH_FAMILY, provider, family);
            return channel == null ? provider.openSocketChannel() : channel;
        } catch (IOException e) {
            throw new ChannelException("Failed to open a socket.", e);
        }
    }

    private final SocketChannelConfig config;

    /**
     * Create a new instance
     */
    public NioSocketChannel(EventLoop eventLoop) {
        this(eventLoop, DEFAULT_SELECTOR_PROVIDER);
    }

    /**
     * Create a new instance using the given {@link SelectorProvider}.
     */
    public NioSocketChannel(EventLoop eventLoop, SelectorProvider provider) {
        this(eventLoop, provider, null);
    }

    /**
     * Create a new instance using the given {@link SelectorProvider} and protocol family (supported only since JDK 15).
     */
    public NioSocketChannel(EventLoop eventLoop, SelectorProvider provider, InternetProtocolFamily family) {
        this(eventLoop, newChannel(provider, family));
    }

    /**
     * Create a new instance using the given {@link SocketChannel}.
     */
    public NioSocketChannel(EventLoop eventLoop, SocketChannel socket) {
        this(null, eventLoop, socket);
    }

    /**
     * Create a new instance
     *
     * @param parent    the {@link Channel} which created this instance or {@code null} if it was created by the user
     * @param eventLoop the {@link EventLoop} to use for IO.
     * @param socket    the {@link SocketChannel} which will be used
     */
    public NioSocketChannel(NioServerSocketChannel parent, EventLoop eventLoop, SocketChannel socket) {
        super(parent, eventLoop, socket);
        config = new NioSocketChannelConfig(this, socket.socket());
    }

    @Override
    public SocketChannelConfig config() {
        return config;
    }

    @Override
    protected SocketChannel javaChannel() {
        return (SocketChannel) super.javaChannel();
    }

    @Override
    public boolean isActive() {
        SocketChannel ch = javaChannel();
        return ch.isOpen() && ch.isConnected();
    }

    @Override
    public boolean isShutdown(ChannelShutdownDirection direction) {
        if (!isActive()) {
            return true;
        }
        switch (direction) {
            case Outbound:
                return javaChannel().socket().isOutputShutdown();
            case Inbound:
                return javaChannel().socket().isInputShutdown();
            default:
                throw new AssertionError();
        }
    }

    @Override
    protected void doShutdown(ChannelShutdownDirection direction) throws Exception {
        switch (direction) {
            case Inbound:
                javaChannel().shutdownInput();
                break;
            case Outbound:
                javaChannel().shutdownOutput();
                break;
            default:
                throw new AssertionError();
        }
    }

    @Override
    protected InetSocketAddress localAddress0() {
        return (InetSocketAddress) javaChannel().socket().getLocalSocketAddress();
    }

    @Override
    protected InetSocketAddress remoteAddress0() {
        return (InetSocketAddress) javaChannel().socket().getRemoteSocketAddress();
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        doBind0(localAddress);
    }

    private void doBind0(SocketAddress localAddress) throws Exception {
        SocketUtils.bind(javaChannel(), localAddress);
    }

    @Override
    protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        if (localAddress != null) {
            doBind0(localAddress);
        }

        boolean success = false;
        try {
            boolean connected = SocketUtils.connect(javaChannel(), remoteAddress);
            if (!connected) {
                selectionKey().interestOps(SelectionKey.OP_CONNECT);
            }
            success = true;
            return connected;
        } finally {
            if (!success) {
                doClose();
            }
        }
    }

    @Override
    protected void doFinishConnect() throws Exception {
        if (!javaChannel().finishConnect()) {
            throw new Error();
        }
    }

    @Override
    protected void doDisconnect() throws Exception {
        doClose();
    }

    @Override
    protected void doClose() throws Exception {
        super.doClose();
        javaChannel().close();
    }

    @Override
    protected int doReadBytes(Buffer buffer) throws Exception {
        final RecvBufferAllocator.Handle allocHandle = recvBufAllocHandle();
        allocHandle.attemptedBytesRead(buffer.writableBytes());
        return buffer.transferFrom(javaChannel(), allocHandle.attemptedBytesRead());
    }

    @Override
    protected int doWriteBytes(Buffer buf) throws Exception {
        final int expectedWrittenBytes = buf.readableBytes();
        return buf.transferTo(javaChannel(), expectedWrittenBytes);
    }

    @Override
    protected long doWriteFileRegion(FileRegion region) throws Exception {
        final long position = region.transferred();
        return region.transferTo(javaChannel(), position);
    }

    private void adjustMaxBytesPerGatheringWrite(int attempted, int written, int oldMaxBytesPerGatheringWrite) {
        // By default we track the SO_SNDBUF when ever it is explicitly set. However some OSes may dynamically change
        // SO_SNDBUF (and other characteristics that determine how much data can be written at once) so we should try
        // make a best effort to adjust as OS behavior changes.
        if (attempted == written) {
            if (attempted << 1 > oldMaxBytesPerGatheringWrite) {
                ((NioSocketChannelConfig) config).setMaxBytesPerGatheringWrite(attempted << 1);
            }
        } else if (attempted > MAX_BYTES_PER_GATHERING_WRITE_ATTEMPTED_LOW_THRESHOLD && written < attempted >>> 1) {
            ((NioSocketChannelConfig) config).setMaxBytesPerGatheringWrite(attempted >>> 1);
        }
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        SocketChannel ch = javaChannel();
        int writeSpinCount = config().getWriteSpinCount();
        do {
            if (in.isEmpty()) {
                // All written so clear OP_WRITE
                clearOpWrite();
                // Directly return here so incompleteWrite(...) is not called.
                return;
            }

            // Ensure the pending writes are made of ByteBufs only.
            int maxBytesPerGatheringWrite = ((NioSocketChannelConfig) config).getMaxBytesPerGatheringWrite();
            ByteBuffer[] nioBuffers = in.nioBuffers(1024, maxBytesPerGatheringWrite);
            int nioBufferCnt = in.nioBufferCount();

            // Always use nioBuffers() to workaround data-corruption.
            // See https://github.com/netty/netty/issues/2761
            switch (nioBufferCnt) {
                case 0:
                    // We have something else beside ByteBuffers to write so fallback to normal writes.
                    writeSpinCount -= doWrite0(in);
                    break;
                case 1: {
                    // Only one ByteBuf so use non-gathering write
                    // Zero length buffers are not added to nioBuffers by ChannelOutboundBuffer, so there is no need
                    // to check if the total size of all the buffers is non-zero.
                    ByteBuffer buffer = nioBuffers[0];
                    int attemptedBytes = buffer.remaining();
                    final int localWrittenBytes = ch.write(buffer);
                    if (localWrittenBytes <= 0) {
                        incompleteWrite(true);
                        return;
                    }
                    adjustMaxBytesPerGatheringWrite(attemptedBytes, localWrittenBytes, maxBytesPerGatheringWrite);
                    in.removeBytes(localWrittenBytes);
                    --writeSpinCount;
                    break;
                }
                default: {
                    // Zero length buffers are not added to nioBuffers by ChannelOutboundBuffer, so there is no need
                    // to check if the total size of all the buffers is non-zero.
                    // We limit the max amount to int above so cast is safe
                    long attemptedBytes = in.nioBufferSize();
                    final long localWrittenBytes = ch.write(nioBuffers, 0, nioBufferCnt);
                    if (localWrittenBytes <= 0) {
                        incompleteWrite(true);
                        return;
                    }
                    // Casting to int is safe because we limit the total amount of data in the nioBuffers to int above.
                    adjustMaxBytesPerGatheringWrite((int) attemptedBytes, (int) localWrittenBytes,
                            maxBytesPerGatheringWrite);
                    in.removeBytes(localWrittenBytes);
                    --writeSpinCount;
                    break;
                }
            }
        } while (writeSpinCount > 0);

        incompleteWrite(writeSpinCount < 0);
    }

    @Override
    protected Executor prepareToClose() {
        try {
            if (javaChannel().isOpen() && config().getSoLinger() > 0) {
                // We need to cancel this key of the channel so we may not end up in a eventloop spin
                // because we try to read or write until the actual close happens which may be later due
                // SO_LINGER handling.
                // See https://github.com/netty/netty/issues/4449
                doDeregister();
                return GlobalEventExecutor.INSTANCE;
            }
        } catch (Throwable ignore) {
            // Ignore the error as the underlying channel may be closed in the meantime and so
            // getSoLinger() may produce an exception. In this case we just return null.
            // See https://github.com/netty/netty/issues/4449
        }
        return null;
    }

    private final class NioSocketChannelConfig extends DefaultSocketChannelConfig {
        private volatile int maxBytesPerGatheringWrite = Integer.MAX_VALUE;
        private NioSocketChannelConfig(NioSocketChannel channel, Socket javaSocket) {
            super(channel, javaSocket);
            calculateMaxBytesPerGatheringWrite();
        }

        @Override
        protected void autoReadCleared() {
            clearReadPending();
        }

        @Override
        public NioSocketChannelConfig setSendBufferSize(int sendBufferSize) {
            super.setSendBufferSize(sendBufferSize);
            calculateMaxBytesPerGatheringWrite();
            return this;
        }

        @Override
        public <T> boolean setOption(ChannelOption<T> option, T value) {
            if (option instanceof NioChannelOption) {
                return NioChannelOption.setOption(jdkChannel(), (NioChannelOption<T>) option, value);
            }
            return super.setOption(option, value);
        }

        @Override
        public <T> T getOption(ChannelOption<T> option) {
            if (option instanceof NioChannelOption) {
                return NioChannelOption.getOption(jdkChannel(), (NioChannelOption<T>) option);
            }
            return super.getOption(option);
        }

        @Override
        public Map<ChannelOption<?>, Object> getOptions() {
            return getOptions(super.getOptions(), NioChannelOption.getOptions(jdkChannel()));
        }

        void setMaxBytesPerGatheringWrite(int maxBytesPerGatheringWrite) {
            this.maxBytesPerGatheringWrite = maxBytesPerGatheringWrite;
        }

        int getMaxBytesPerGatheringWrite() {
            return maxBytesPerGatheringWrite;
        }

        private void calculateMaxBytesPerGatheringWrite() {
            // Multiply by 2 to give some extra space in case the OS can process write data faster than we can provide.
            int newSendBufferSize = getSendBufferSize() << 1;
            if (newSendBufferSize > 0) {
                setMaxBytesPerGatheringWrite(newSendBufferSize);
            }
        }

        private SocketChannel jdkChannel() {
            return ((NioSocketChannel) channel).javaChannel();
        }
    }
}
