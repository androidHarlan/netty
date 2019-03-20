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

import static java.util.Objects.requireNonNull;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.socket.ChannelOutputShutdownEvent;
import io.netty.channel.socket.ChannelOutputShutdownException;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.ThrowableUtil;
import io.netty.util.internal.UnstableApi;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NotYetConnectedException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * A skeletal {@link Channel} implementation.
 */
public abstract class AbstractChannel implements Channel {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractChannel.class);

    private static final ClosedChannelException ENSURE_OPEN_CLOSED_CHANNEL_EXCEPTION = ThrowableUtil.unknownStackTrace(
            new ExtendedClosedChannelException(null), AbstractUnsafe.class, "ensureOpen(...)");
    private static final ClosedChannelException CLOSE_CLOSED_CHANNEL_EXCEPTION = ThrowableUtil.unknownStackTrace(
            new ClosedChannelException(), AbstractUnsafe.class, "close(...)");
    private static final ClosedChannelException WRITE_CLOSED_CHANNEL_EXCEPTION = ThrowableUtil.unknownStackTrace(
            new ExtendedClosedChannelException(null), AbstractUnsafe.class, "write(...)");
    private static final ClosedChannelException FLUSH0_CLOSED_CHANNEL_EXCEPTION = ThrowableUtil.unknownStackTrace(
            new ExtendedClosedChannelException(null), AbstractUnsafe.class, "flush0()");
    private static final NotYetConnectedException FLUSH0_NOT_YET_CONNECTED_EXCEPTION = ThrowableUtil.unknownStackTrace(
            new NotYetConnectedException(), AbstractUnsafe.class, "flush0()");
    private static final AtomicReferenceFieldUpdater<AbstractChannel, AtomicReferenceArray> updater =
            AtomicReferenceFieldUpdater.newUpdater(AbstractChannel.class, AtomicReferenceArray.class, "attributes");

    private static final int BUCKET_SIZE = 4;
    private static final int MASK = BUCKET_SIZE  - 1;

    // Initialize lazily to reduce memory consumption; updated by AtomicReferenceFieldUpdater above.
    @SuppressWarnings("UnusedDeclaration")
    private volatile AtomicReferenceArray<DefaultAttribute<?>> attributes;

    private final Channel parent;
    private final ChannelId id;
    private final Unsafe unsafe;
    private final ChannelPipeline pipeline;
    private final ChannelFuture succeedFuture;
    private final VoidChannelPromise unsafeVoidPromise = new VoidChannelPromise(this, false);
    private final CloseFuture closeFuture = new CloseFuture(this);

    private volatile SocketAddress localAddress;
    private volatile SocketAddress remoteAddress;
    private final EventLoop eventLoop;
    private volatile boolean registered;
    private boolean closeInitiated;
    private Throwable initialCloseCause;

    /** Cache for the string representation of this channel */
    private boolean strValActive;
    private String strVal;

    /**
     * Creates a new instance.
     *
     * @param parent
     *        the parent of this channel. {@code null} if there's no parent.
     */
    protected AbstractChannel(Channel parent, EventLoop eventLoop) {
        this.parent = parent;
        this.eventLoop = validateEventLoop(eventLoop);
        id = newId();
        succeedFuture = new SucceededChannelFuture(this, eventLoop);
        unsafe = newUnsafe();
        pipeline = newChannelPipeline();
    }

    /**
     * Creates a new instance.
     *
     * @param parent
     *        the parent of this channel. {@code null} if there's no parent.
     */
    protected AbstractChannel(Channel parent, EventLoop eventLoop, ChannelId id) {
        this.parent = parent;
        this.eventLoop = validateEventLoop(eventLoop);
        this.id = id;
        succeedFuture = new SucceededChannelFuture(this, eventLoop);
        unsafe = newUnsafe();
        pipeline = newChannelPipeline();
    }

    private EventLoop validateEventLoop(EventLoop eventLoop) {
        return requireNonNull(eventLoop, "eventLoop");
    }

    @Override
    public final ChannelId id() {
        return id;
    }

    /**
     * Returns a new {@link DefaultChannelId} instance. Subclasses may override this method to assign custom
     * {@link ChannelId}s to {@link Channel}s that use the {@link AbstractChannel#AbstractChannel(Channel, EventLoop)}
     * constructor.
     */
    protected ChannelId newId() {
        return DefaultChannelId.newInstance();
    }

    /**
     * Returns a new {@link ChannelPipeline} instance.
     */
    protected ChannelPipeline newChannelPipeline() {
        return new DefaultChannelPipeline(this);
    }

    @Override
    public boolean isWritable() {
        ChannelOutboundBuffer buf = unsafe.outboundBuffer();
        return buf != null && buf.isWritable();
    }

    @Override
    public long bytesBeforeUnwritable() {
        ChannelOutboundBuffer buf = unsafe.outboundBuffer();
        // isWritable() is currently assuming if there is no outboundBuffer then the channel is not writable.
        // We should be consistent with that here.
        return buf != null ? buf.bytesBeforeUnwritable() : 0;
    }

    @Override
    public long bytesBeforeWritable() {
        ChannelOutboundBuffer buf = unsafe.outboundBuffer();
        // isWritable() is currently assuming if there is no outboundBuffer then the channel is not writable.
        // We should be consistent with that here.
        return buf != null ? buf.bytesBeforeWritable() : Long.MAX_VALUE;
    }

    @Override
    public Channel parent() {
        return parent;
    }

    @Override
    public ChannelPipeline pipeline() {
        return pipeline;
    }

    @Override
    public ByteBufAllocator alloc() {
        return config().getAllocator();
    }

    @Override
    public EventLoop eventLoop() {
        return eventLoop;
    }

    @Override
    public SocketAddress localAddress() {
        SocketAddress localAddress = this.localAddress;
        if (localAddress == null) {
            try {
                this.localAddress = localAddress = unsafe().localAddress();
            } catch (Error e) {
                throw e;
            } catch (Throwable t) {
                // Sometimes fails on a closed socket in Windows.
                return null;
            }
        }
        return localAddress;
    }

    /**
     * @deprecated no use-case for this.
     */
    @Deprecated
    protected void invalidateLocalAddress() {
        localAddress = null;
    }

    @Override
    public SocketAddress remoteAddress() {
        SocketAddress remoteAddress = this.remoteAddress;
        if (remoteAddress == null) {
            try {
                this.remoteAddress = remoteAddress = unsafe().remoteAddress();
            } catch (Error e) {
                throw e;
            } catch (Throwable t) {
                // Sometimes fails on a closed socket in Windows.
                return null;
            }
        }
        return remoteAddress;
    }

    /**
     * @deprecated no use-case for this.
     */
    @Deprecated
    protected void invalidateRemoteAddress() {
        remoteAddress = null;
    }

    @Override
    public boolean isRegistered() {
        return registered;
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress) {
        return pipeline.bind(localAddress);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress) {
        return pipeline.connect(remoteAddress);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        return pipeline.connect(remoteAddress, localAddress);
    }

    @Override
    public ChannelFuture disconnect() {
        return pipeline.disconnect();
    }

    @Override
    public ChannelFuture close() {
        return pipeline.close();
    }

    @Override
    public ChannelFuture register() {
        return pipeline.register();
    }

    @Override
    public ChannelFuture deregister() {
        return pipeline.deregister();
    }

    @Override
    public Channel flush() {
        pipeline.flush();
        return this;
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
        return pipeline.bind(localAddress, promise);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
        return pipeline.connect(remoteAddress, promise);
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        return pipeline.connect(remoteAddress, localAddress, promise);
    }

    @Override
    public ChannelFuture disconnect(ChannelPromise promise) {
        return pipeline.disconnect(promise);
    }

    @Override
    public ChannelFuture close(ChannelPromise promise) {
        return pipeline.close(promise);
    }

    @Override
    public ChannelFuture register(ChannelPromise promise) {
        return pipeline.register(promise);
    }

    @Override
    public ChannelFuture deregister(ChannelPromise promise) {
        return pipeline.deregister(promise);
    }

    @Override
    public Channel read() {
        pipeline.read();
        return this;
    }

    @Override
    public ChannelFuture write(Object msg) {
        return pipeline.write(msg);
    }

    @Override
    public ChannelFuture write(Object msg, ChannelPromise promise) {
        return pipeline.write(msg, promise);
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg) {
        return pipeline.writeAndFlush(msg);
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
        return pipeline.writeAndFlush(msg, promise);
    }

    @Override
    public ChannelPromise newPromise() {
        return new DefaultChannelPromise(this, eventLoop);
    }

    @Override
    public ChannelProgressivePromise newProgressivePromise() {
        return new DefaultChannelProgressivePromise(this, eventLoop);
    }

    @Override
    public ChannelFuture newSucceededFuture() {
        return succeedFuture;
    }

    @Override
    public ChannelFuture newFailedFuture(Throwable cause) {
        return new FailedChannelFuture(this, eventLoop, cause);
    }

    @Override
    public ChannelFuture closeFuture() {
        return closeFuture;
    }

    @Override
    public Unsafe unsafe() {
        return unsafe;
    }

    /**
     * Create a new {@link AbstractUnsafe} instance which will be used for the life-time of the {@link Channel}
     */
    protected abstract AbstractUnsafe newUnsafe();

    /**
     * Returns the ID of this channel.
     */
    @Override
    public final int hashCode() {
        return id.hashCode();
    }

    /**
     * Returns {@code true} if and only if the specified object is identical
     * with this channel (i.e: {@code this == o}).
     */
    @Override
    public final boolean equals(Object o) {
        return this == o;
    }

    @Override
    public final int compareTo(Channel o) {
        if (this == o) {
            return 0;
        }

        return id().compareTo(o.id());
    }

    /**
     * Returns the {@link String} representation of this channel.  The returned
     * string contains the {@linkplain #hashCode() ID}, {@linkplain #localAddress() local address},
     * and {@linkplain #remoteAddress() remote address} of this channel for
     * easier identification.
     */
    @Override
    public String toString() {
        boolean active = isActive();
        if (strValActive == active && strVal != null) {
            return strVal;
        }

        SocketAddress remoteAddr = remoteAddress();
        SocketAddress localAddr = localAddress();
        if (remoteAddr != null) {
            StringBuilder buf = new StringBuilder(96)
                .append("[id: 0x")
                .append(id.asShortText())
                .append(", L:")
                .append(localAddr)
                .append(active? " - " : " ! ")
                .append("R:")
                .append(remoteAddr)
                .append(']');
            strVal = buf.toString();
        } else if (localAddr != null) {
            StringBuilder buf = new StringBuilder(64)
                .append("[id: 0x")
                .append(id.asShortText())
                .append(", L:")
                .append(localAddr)
                .append(']');
            strVal = buf.toString();
        } else {
            StringBuilder buf = new StringBuilder(16)
                .append("[id: 0x")
                .append(id.asShortText())
                .append(']');
            strVal = buf.toString();
        }

        strValActive = active;
        return strVal;
    }

    @Override
    public final ChannelPromise voidPromise() {
        return pipeline.voidPromise();
    }

    protected final void readIfIsAutoRead() {
        if (config().isAutoRead()) {
            read();
        }
    }

    @SuppressWarnings("unchecked")
    public <T> Attribute<T> attr(AttributeKey<T> key) {
        requireNonNull(key, "key");
        AtomicReferenceArray<DefaultAttribute<?>> attributes = this.attributes;
        if (attributes == null) {
            // Not using ConcurrentHashMap due to high memory consumption.
            attributes = new AtomicReferenceArray<>(BUCKET_SIZE);

            if (!updater.compareAndSet(this, null, attributes)) {
                attributes = this.attributes;
            }
        }

        int i = index(key);
        DefaultAttribute<?> head = attributes.get(i);
        if (head == null) {
            // No head exists yet which means we may be able to add the attribute without synchronization and just
            // use compare and set. At worst we need to fallback to synchronization and waste two allocations.
            head = new DefaultAttribute();
            DefaultAttribute<T> attr = new DefaultAttribute<>(head, key);
            head.next = attr;
            attr.prev = head;
            if (attributes.compareAndSet(i, null, head)) {
                // we were able to add it so return the attr right away
                return attr;
            } else {
                head = attributes.get(i);
            }
        }

        synchronized (head) {
            DefaultAttribute<?> curr = head;
            for (;;) {
                DefaultAttribute<?> next = curr.next;
                if (next == null) {
                    DefaultAttribute<T> attr = new DefaultAttribute<>(head, key);
                    curr.next = attr;
                    attr.prev = curr;
                    return attr;
                }

                if (next.key == key && !next.removed) {
                    return (Attribute<T>) next;
                }
                curr = next;
            }
        }
    }

    public <T> boolean hasAttr(AttributeKey<T> key) {
        requireNonNull(key, "key");
        AtomicReferenceArray<DefaultAttribute<?>> attributes = this.attributes;
        if (attributes == null) {
            // no attribute exists
            return false;
        }

        int i = index(key);
        DefaultAttribute<?> head = attributes.get(i);
        if (head == null) {
            // No attribute exists which point to the bucket in which the head should be located
            return false;
        }

        // We need to synchronize on the head.
        synchronized (head) {
            // Start with head.next as the head itself does not store an attribute.
            DefaultAttribute<?> curr = head.next;
            while (curr != null) {
                if (curr.key == key && !curr.removed) {
                    return true;
                }
                curr = curr.next;
            }
            return false;
        }
    }

    private static int index(AttributeKey<?> key) {
        return key.id() & MASK;
    }

    @SuppressWarnings("serial")
    private static final class DefaultAttribute<T> extends AtomicReference<T> implements Attribute<T> {

        private static final long serialVersionUID = -2661411462200283011L;

        // The head of the linked-list this attribute belongs to
        private final DefaultAttribute<?> head;
        private final AttributeKey<T> key;

        // Double-linked list to prev and next node to allow fast removal
        private DefaultAttribute<?> prev;
        private DefaultAttribute<?> next;

        // Will be set to true one the attribute is removed via getAndRemove() or remove()
        private volatile boolean removed;

        DefaultAttribute(DefaultAttribute<?> head, AttributeKey<T> key) {
            this.head = head;
            this.key = key;
        }

        // Special constructor for the head of the linked-list.
        DefaultAttribute() {
            head = this;
            key = null;
        }

        @Override
        public AttributeKey<T> key() {
            return key;
        }

        @Override
        public T setIfAbsent(T value) {
            while (!compareAndSet(null, value)) {
                T old = get();
                if (old != null) {
                    return old;
                }
            }
            return null;
        }

        @Override
        public T getAndRemove() {
            removed = true;
            T oldValue = getAndSet(null);
            remove0();
            return oldValue;
        }

        @Override
        public void remove() {
            removed = true;
            set(null);
            remove0();
        }

        private void remove0() {
            synchronized (head) {
                if (prev == null) {
                    // Removed before.
                    return;
                }

                prev.next = next;

                if (next != null) {
                    next.prev = prev;
                }

                // Null out prev and next - this will guard against multiple remove0() calls which may corrupt
                // the linked list for the bucket.
                prev = null;
                next = null;
            }
        }
    }

    /**
     * {@link Unsafe} implementation which sub-classes must extend and use.
     */
    protected abstract class AbstractUnsafe implements Unsafe {

        private volatile ChannelOutboundBuffer outboundBuffer = new ChannelOutboundBuffer(AbstractChannel.this);
        private RecvByteBufAllocator.Handle recvHandle;
        private MessageSizeEstimator.Handle estimatorHandler;

        private boolean inFlush0;
        /** true if the channel has never been registered, false otherwise */
        private boolean neverRegistered = true;

        private void assertEventLoop() {
            assert eventLoop.inEventLoop();
        }

        @Override
        public RecvByteBufAllocator.Handle recvBufAllocHandle() {
            if (recvHandle == null) {
                recvHandle = config().getRecvByteBufAllocator().newHandle();
            }
            return recvHandle;
        }

        @Override
        public final ChannelOutboundBuffer outboundBuffer() {
            return outboundBuffer;
        }

        @Override
        public final SocketAddress localAddress() {
            return localAddress0();
        }

        @Override
        public final SocketAddress remoteAddress() {
            return remoteAddress0();
        }

        @Override
        public final void register(final ChannelPromise promise) {
            assertEventLoop();

            if (isRegistered()) {
                promise.setFailure(new IllegalStateException("registered to an event loop already"));
                return;
            }

            try {
                // check if the channel is still open as it could be closed in the mean time when the register
                // call was outside of the eventLoop
                if (!promise.setUncancellable() || !ensureOpen(promise)) {
                    return;
                }
                boolean firstRegistration = neverRegistered;
                doRegister();
                neverRegistered = false;
                registered = true;

                safeSetSuccess(promise);
                pipeline.fireChannelRegistered();
                // Only fire a channelActive if the channel has never been registered. This prevents firing
                // multiple channel actives if the channel is deregistered and re-registered.
                if (isActive()) {
                    if (firstRegistration) {
                        pipeline.fireChannelActive();
                    }
                    readIfIsAutoRead();
                }
            } catch (Throwable t) {
                // Close the channel directly to avoid FD leak.
                closeForcibly();
                closeFuture.setClosed();
                safeSetFailure(promise, t);
            }
        }

        @Override
        public final void bind(final SocketAddress localAddress, final ChannelPromise promise) {
            assertEventLoop();

            if (!promise.setUncancellable() || !ensureOpen(promise)) {
                return;
            }

            // See: https://github.com/netty/netty/issues/576
            if (Boolean.TRUE.equals(config().getOption(ChannelOption.SO_BROADCAST)) &&
                localAddress instanceof InetSocketAddress &&
                !((InetSocketAddress) localAddress).getAddress().isAnyLocalAddress() &&
                !PlatformDependent.isWindows() && !PlatformDependent.maybeSuperUser()) {
                // Warn a user about the fact that a non-root user can't receive a
                // broadcast packet on *nix if the socket is bound on non-wildcard address.
                logger.warn(
                        "A non-root user can't receive a broadcast packet if the socket " +
                        "is not bound to a wildcard address; binding to a non-wildcard " +
                        "address (" + localAddress + ") anyway as requested.");
            }

            boolean wasActive = isActive();
            try {
                doBind(localAddress);
            } catch (Throwable t) {
                safeSetFailure(promise, t);
                closeIfClosed();
                return;
            }

            if (!wasActive && isActive()) {
                invokeLater(() -> {
                    pipeline.fireChannelActive();
                    readIfIsAutoRead();
                });
            }

            safeSetSuccess(promise);
        }

        @Override
        public final void disconnect(final ChannelPromise promise) {
            assertEventLoop();

            if (!promise.setUncancellable()) {
                return;
            }

            boolean wasActive = isActive();
            try {
                doDisconnect();
            } catch (Throwable t) {
                safeSetFailure(promise, t);
                closeIfClosed();
                return;
            }

            if (wasActive && !isActive()) {
                invokeLater(pipeline::fireChannelInactive);
            }

            safeSetSuccess(promise);
            closeIfClosed(); // doDisconnect() might have closed the channel
        }

        @Override
        public final void close(final ChannelPromise promise) {
            assertEventLoop();

            close(promise, CLOSE_CLOSED_CHANNEL_EXCEPTION, CLOSE_CLOSED_CHANNEL_EXCEPTION, false);
        }

        /**
         * Shutdown the output portion of the corresponding {@link Channel}.
         * For example this will clean up the {@link ChannelOutboundBuffer} and not allow any more writes.
         */
        @UnstableApi
        public final void shutdownOutput(final ChannelPromise promise) {
            assertEventLoop();
            shutdownOutput(promise, null);
        }

        /**
         * Shutdown the output portion of the corresponding {@link Channel}.
         * For example this will clean up the {@link ChannelOutboundBuffer} and not allow any more writes.
         * @param cause The cause which may provide rational for the shutdown.
         */
        private void shutdownOutput(final ChannelPromise promise, Throwable cause) {
            if (!promise.setUncancellable()) {
                return;
            }

            final ChannelOutboundBuffer outboundBuffer = this.outboundBuffer;
            if (outboundBuffer == null) {
                promise.setFailure(CLOSE_CLOSED_CHANNEL_EXCEPTION);
                return;
            }
            this.outboundBuffer = null; // Disallow adding any messages and flushes to outboundBuffer.

            final Throwable shutdownCause = cause == null ?
                    new ChannelOutputShutdownException("Channel output shutdown") :
                    new ChannelOutputShutdownException("Channel output shutdown", cause);
            Executor closeExecutor = prepareToClose();
            if (closeExecutor != null) {
                closeExecutor.execute(() -> {
                    try {
                        // Execute the shutdown.
                        doShutdownOutput();
                        promise.setSuccess();
                    } catch (Throwable err) {
                        promise.setFailure(err);
                    } finally {
                        // Dispatch to the EventLoop
                        eventLoop().execute(() ->
                                closeOutboundBufferForShutdown(pipeline, outboundBuffer, shutdownCause));
                    }
                });
            } else {
                try {
                    // Execute the shutdown.
                    doShutdownOutput();
                    promise.setSuccess();
                } catch (Throwable err) {
                    promise.setFailure(err);
                } finally {
                    closeOutboundBufferForShutdown(pipeline, outboundBuffer, shutdownCause);
                }
            }
        }

        private void closeOutboundBufferForShutdown(
                ChannelPipeline pipeline, ChannelOutboundBuffer buffer, Throwable cause) {
            buffer.failFlushed(cause, false);
            buffer.close(cause, true);
            pipeline.fireUserEventTriggered(ChannelOutputShutdownEvent.INSTANCE);
        }

        private void close(final ChannelPromise promise, final Throwable cause,
                           final ClosedChannelException closeCause, final boolean notify) {
            if (!promise.setUncancellable()) {
                return;
            }

            if (closeInitiated) {
                if (closeFuture.isDone()) {
                    // Closed already.
                    safeSetSuccess(promise);
                } else if (!(promise instanceof VoidChannelPromise)) { // Only needed if no VoidChannelPromise.
                    // This means close() was called before so we just register a listener and return
                    closeFuture.addListener((ChannelFutureListener) future -> promise.setSuccess());
                }
                return;
            }

            closeInitiated = true;

            final boolean wasActive = isActive();
            final ChannelOutboundBuffer outboundBuffer = this.outboundBuffer;
            this.outboundBuffer = null; // Disallow adding any messages and flushes to outboundBuffer.
            Executor closeExecutor = prepareToClose();
            if (closeExecutor != null) {
                closeExecutor.execute(() -> {
                    try {
                        // Execute the close.
                        doClose0(promise);
                    } finally {
                        // Call invokeLater so closeAndDeregister is executed in the EventLoop again!
                        invokeLater(() -> {
                            if (outboundBuffer != null) {
                                // Fail all the queued messages
                                outboundBuffer.failFlushed(cause, notify);
                                outboundBuffer.close(closeCause);
                            }
                            fireChannelInactiveAndDeregister(wasActive);
                        });
                    }
                });
            } else {
                try {
                    // Close the channel and fail the queued messages in all cases.
                    doClose0(promise);
                } finally {
                    if (outboundBuffer != null) {
                        // Fail all the queued messages.
                        outboundBuffer.failFlushed(cause, notify);
                        outboundBuffer.close(closeCause);
                    }
                }
                if (inFlush0) {
                    invokeLater(() -> fireChannelInactiveAndDeregister(wasActive));
                } else {
                    fireChannelInactiveAndDeregister(wasActive);
                }
            }
        }

        private void doClose0(ChannelPromise promise) {
            try {
                doClose();
                closeFuture.setClosed();
                safeSetSuccess(promise);
            } catch (Throwable t) {
                closeFuture.setClosed();
                safeSetFailure(promise, t);
            }
        }

        private void fireChannelInactiveAndDeregister(final boolean wasActive) {
            deregister(voidPromise(), wasActive && !isActive());
        }

        @Override
        public final void closeForcibly() {
            try {
                doClose();
            } catch (Exception e) {
                logger.warn("Failed to close a channel.", e);
            }
        }

        @Override
        public final void deregister(final ChannelPromise promise) {
            assertEventLoop();

            deregister(promise, false);
        }

        private void deregister(final ChannelPromise promise, final boolean fireChannelInactive) {
            if (!promise.setUncancellable()) {
                return;
            }

            if (!registered) {
                safeSetSuccess(promise);
                return;
            }

            // As a user may call deregister() from within any method while doing processing in the ChannelPipeline,
            // we need to ensure we do the actual deregister operation later. This is needed as for example,
            // we may be in the ByteToMessageDecoder.callDecode(...) method and so still try to do processing in
            // the old EventLoop while the user already registered the Channel to a new EventLoop. Without delay,
            // the deregister operation this could lead to have a handler invoked by different EventLoop and so
            // threads.
            //
            // See:
            // https://github.com/netty/netty/issues/4435
            invokeLater(() -> {
                try {
                    doDeregister();
                } catch (Throwable t) {
                    logger.warn("Unexpected exception occurred while deregistering a channel.", t);
                } finally {
                    if (fireChannelInactive) {
                        pipeline.fireChannelInactive();
                    }
                    // Some transports like local and AIO does not allow the deregistration of
                    // an open channel.  Their doDeregister() calls close(). Consequently,
                    // close() calls deregister() again - no need to fire channelUnregistered, so check
                    // if it was registered.
                    if (registered) {
                        registered = false;
                        pipeline.fireChannelUnregistered();
                    }
                    safeSetSuccess(promise);
                }
            });
        }

        @Override
        public final void beginRead() {
            assertEventLoop();

            if (!isActive()) {
                return;
            }

            try {
                doBeginRead();
            } catch (final Exception e) {
                invokeLater(() -> pipeline.fireExceptionCaught(e));
                close(voidPromise());
            }
        }

        @Override
        public final void write(Object msg, ChannelPromise promise) {
            assertEventLoop();

            ChannelOutboundBuffer outboundBuffer = this.outboundBuffer;
            if (outboundBuffer == null) {
                // If the outboundBuffer is null we know the channel was closed and so
                // need to fail the future right away. If it is not null the handling of the rest
                // will be done in flush0()
                // See https://github.com/netty/netty/issues/2362
                safeSetFailure(promise, newWriteException(initialCloseCause));
                // release message now to prevent resource-leak
                ReferenceCountUtil.release(msg);
                return;
            }

            int size;
            try {
                msg = filterOutboundMessage(msg);
                if (estimatorHandler == null) {
                    estimatorHandler = config().getMessageSizeEstimator().newHandle();
                }
                size = estimatorHandler.size(msg);
                if (size < 0) {
                    size = 0;
                }
            } catch (Throwable t) {
                safeSetFailure(promise, t);
                ReferenceCountUtil.release(msg);
                return;
            }

            outboundBuffer.addMessage(msg, size, promise);
        }

        @Override
        public final void flush() {
            assertEventLoop();

            ChannelOutboundBuffer outboundBuffer = this.outboundBuffer;
            if (outboundBuffer == null) {
                return;
            }

            outboundBuffer.addFlush();
            flush0();
        }

        @SuppressWarnings("deprecation")
        protected void flush0() {
            if (inFlush0) {
                // Avoid re-entrance
                return;
            }

            final ChannelOutboundBuffer outboundBuffer = this.outboundBuffer;
            if (outboundBuffer == null || outboundBuffer.isEmpty()) {
                return;
            }

            inFlush0 = true;

            // Mark all pending write requests as failure if the channel is inactive.
            if (!isActive()) {
                try {
                    if (isOpen()) {
                        outboundBuffer.failFlushed(FLUSH0_NOT_YET_CONNECTED_EXCEPTION, true);
                    } else {
                        // Do not trigger channelWritabilityChanged because the channel is closed already.
                        outboundBuffer.failFlushed(newFlush0Exception(initialCloseCause), false);
                    }
                } finally {
                    inFlush0 = false;
                }
                return;
            }

            try {
                doWrite(outboundBuffer);
            } catch (Throwable t) {
                if (t instanceof IOException && config().isAutoClose()) {
                    /**
                     * Just call {@link #close(ChannelPromise, Throwable, boolean)} here which will take care of
                     * failing all flushed messages and also ensure the actual close of the underlying transport
                     * will happen before the promises are notified.
                     *
                     * This is needed as otherwise {@link #isActive()} , {@link #isOpen()} and {@link #isWritable()}
                     * may still return {@code true} even if the channel should be closed as result of the exception.
                     */
                    initialCloseCause = t;
                    close(voidPromise(), t, newFlush0Exception(t), false);
                } else {
                    try {
                        shutdownOutput(voidPromise(), t);
                    } catch (Throwable t2) {
                        initialCloseCause = t;
                        close(voidPromise(), t2, newFlush0Exception(t), false);
                    }
                }
            } finally {
                inFlush0 = false;
            }
        }

        private ClosedChannelException newWriteException(Throwable cause) {
            if (cause == null) {
                return WRITE_CLOSED_CHANNEL_EXCEPTION;
            }
            return ThrowableUtil.unknownStackTrace(
                    new ExtendedClosedChannelException(cause), AbstractUnsafe.class, "write(...)");
        }

        private ClosedChannelException newFlush0Exception(Throwable cause) {
            if (cause == null) {
                return FLUSH0_CLOSED_CHANNEL_EXCEPTION;
            }
            return ThrowableUtil.unknownStackTrace(
                    new ExtendedClosedChannelException(cause), AbstractUnsafe.class, "flush0()");
        }

        private ClosedChannelException newEnsureOpenException(Throwable cause) {
            if (cause == null) {
                return ENSURE_OPEN_CLOSED_CHANNEL_EXCEPTION;
            }
            return ThrowableUtil.unknownStackTrace(
                    new ExtendedClosedChannelException(cause), AbstractUnsafe.class, "ensureOpen(...)");
        }

        @Override
        public final ChannelPromise voidPromise() {
            assertEventLoop();

            return unsafeVoidPromise;
        }

        protected final boolean ensureOpen(ChannelPromise promise) {
            if (isOpen()) {
                return true;
            }

            safeSetFailure(promise, newEnsureOpenException(initialCloseCause));
            return false;
        }

        /**
         * Marks the specified {@code promise} as success.  If the {@code promise} is done already, log a message.
         */
        protected final void safeSetSuccess(ChannelPromise promise) {
            if (!(promise instanceof VoidChannelPromise) && !promise.trySuccess()) {
                logger.warn("Failed to mark a promise as success because it is done already: {}", promise);
            }
        }

        /**
         * Marks the specified {@code promise} as failure.  If the {@code promise} is done already, log a message.
         */
        protected final void safeSetFailure(ChannelPromise promise, Throwable cause) {
            if (!(promise instanceof VoidChannelPromise) && !promise.tryFailure(cause)) {
                logger.warn("Failed to mark a promise as failure because it's done already: {}", promise, cause);
            }
        }

        protected final void closeIfClosed() {
            if (isOpen()) {
                return;
            }
            close(voidPromise());
        }

        private void invokeLater(Runnable task) {
            try {
                // This method is used by outbound operation implementations to trigger an inbound event later.
                // They do not trigger an inbound event immediately because an outbound operation might have been
                // triggered by another inbound event handler method.  If fired immediately, the call stack
                // will look like this for example:
                //
                //   handlerA.inboundBufferUpdated() - (1) an inbound handler method closes a connection.
                //   -> handlerA.ctx.close()
                //      -> channel.unsafe.close()
                //         -> handlerA.channelInactive() - (2) another inbound handler method called while in (1) yet
                //
                // which means the execution of two inbound handler methods of the same handler overlap undesirably.
                eventLoop().execute(task);
            } catch (RejectedExecutionException e) {
                logger.warn("Can't invoke task later as EventLoop rejected it", e);
            }
        }

        /**
         * Appends the remote address to the message of the exceptions caused by connection attempt failure.
         */
        protected final Throwable annotateConnectException(Throwable cause, SocketAddress remoteAddress) {
            if (cause instanceof ConnectException) {
                return new AnnotatedConnectException((ConnectException) cause, remoteAddress);
            }
            if (cause instanceof NoRouteToHostException) {
                return new AnnotatedNoRouteToHostException((NoRouteToHostException) cause, remoteAddress);
            }
            if (cause instanceof SocketException) {
                return new AnnotatedSocketException((SocketException) cause, remoteAddress);
            }

            return cause;
        }

        /**
         * Prepares to close the {@link Channel}. If this method returns an {@link Executor}, the
         * caller must call the {@link Executor#execute(Runnable)} method with a task that calls
         * {@link #doClose()} on the returned {@link Executor}. If this method returns {@code null},
         * {@link #doClose()} must be called from the caller thread. (i.e. {@link EventLoop})
         */
        protected Executor prepareToClose() {
            return null;
        }
    }

    /**
     * Returns the {@link SocketAddress} which is bound locally.
     */
    protected abstract SocketAddress localAddress0();

    /**
     * Return the {@link SocketAddress} which the {@link Channel} is connected to.
     */
    protected abstract SocketAddress remoteAddress0();

    /**
     * Is called after the {@link Channel} is registered with its {@link EventLoop} as part of the register process.
     *
     * Sub-classes may override this method
     */
    protected void doRegister() throws Exception {
        eventLoop().unsafe().register(this);
    }

    /**
     * Bind the {@link Channel} to the {@link SocketAddress}
     */
    protected abstract void doBind(SocketAddress localAddress) throws Exception;

    /**
     * Disconnect this {@link Channel} from its remote peer
     */
    protected abstract void doDisconnect() throws Exception;

    /**
     * Close the {@link Channel}
     */
    protected abstract void doClose() throws Exception;

    /**
     * Called when conditions justify shutting down the output portion of the channel. This may happen if a write
     * operation throws an exception.
     */
    @UnstableApi
    protected void doShutdownOutput() throws Exception {
        doClose();
    }

    /**
     * Deregister the {@link Channel} from its {@link EventLoop}.
     *
     * Sub-classes may override this method
     */
    protected void doDeregister() throws Exception {
        eventLoop().unsafe().deregister(this);
    }

    /**
     * Schedule a read operation.
     */
    protected abstract void doBeginRead() throws Exception;

    /**
     * Flush the content of the given buffer to the remote peer.
     */
    protected abstract void doWrite(ChannelOutboundBuffer in) throws Exception;

    /**
     * Invoked when a new message is added to a {@link ChannelOutboundBuffer} of this {@link AbstractChannel}, so that
     * the {@link Channel} implementation converts the message to another. (e.g. heap buffer -> direct buffer)
     */
    protected Object filterOutboundMessage(Object msg) throws Exception {
        return msg;
    }

    protected void validateFileRegion(DefaultFileRegion region, long position) throws IOException {
        DefaultFileRegion.validate(region, position);
    }

    static final class CloseFuture extends DefaultChannelPromise {

        CloseFuture(AbstractChannel ch) {
            super(ch);
        }

        @Override
        public ChannelPromise setSuccess() {
            throw new IllegalStateException();
        }

        @Override
        public ChannelPromise setFailure(Throwable cause) {
            throw new IllegalStateException();
        }

        @Override
        public boolean trySuccess() {
            throw new IllegalStateException();
        }

        @Override
        public boolean tryFailure(Throwable cause) {
            throw new IllegalStateException();
        }

        boolean setClosed() {
            return super.trySuccess();
        }
    }

    private static final class AnnotatedConnectException extends ConnectException {

        private static final long serialVersionUID = 3901958112696433556L;

        AnnotatedConnectException(ConnectException exception, SocketAddress remoteAddress) {
            super(exception.getMessage() + ": " + remoteAddress);
            initCause(exception);
            setStackTrace(exception.getStackTrace());
        }

        @Override
        public Throwable fillInStackTrace() {
            return this;
        }
    }

    private static final class AnnotatedNoRouteToHostException extends NoRouteToHostException {

        private static final long serialVersionUID = -6801433937592080623L;

        AnnotatedNoRouteToHostException(NoRouteToHostException exception, SocketAddress remoteAddress) {
            super(exception.getMessage() + ": " + remoteAddress);
            initCause(exception);
            setStackTrace(exception.getStackTrace());
        }

        @Override
        public Throwable fillInStackTrace() {
            return this;
        }
    }

    private static final class AnnotatedSocketException extends SocketException {

        private static final long serialVersionUID = 3896743275010454039L;

        AnnotatedSocketException(SocketException exception, SocketAddress remoteAddress) {
            super(exception.getMessage() + ": " + remoteAddress);
            initCause(exception);
            setStackTrace(exception.getStackTrace());
        }

        @Override
        public Throwable fillInStackTrace() {
            return this;
        }
    }
}
