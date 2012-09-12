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
package io.netty.bootstrap;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.Map.Entry;

/**
 * A {@link Bootstrap} that makes it easy to bootstrap a {@link Channel} to use
 * for clients.
 *
 */
public class Bootstrap extends AbstractBootstrap<Bootstrap> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Bootstrap.class);
    private SocketAddress remoteAddress;


    /**
     * The {@link SocketAddress} to connect to once the {@link #connect()} method
     * is called.
     */
    public Bootstrap remoteAddress(SocketAddress remoteAddress) {
        this.remoteAddress = remoteAddress;
        return this;
    }

    /**
     * See {@link #remoteAddress(SocketAddress)}
     */
    public Bootstrap remoteAddress(String host, int port) {
        remoteAddress = new InetSocketAddress(host, port);
        return this;
    }

    /**
     * See {@link #remoteAddress(SocketAddress)}
     */
    public Bootstrap remoteAddress(InetAddress host, int port) {
        remoteAddress = new InetSocketAddress(host, port);
        return this;
    }

    @Override
    public ChannelFuture bind(ChannelFuture future) {
        validate(future);
        if (localAddress() == null) {
            throw new IllegalStateException("localAddress not set");
        }

        try {
            init(future.channel());
        } catch (Throwable t) {
            future.setFailure(t);
            return future;
        }

        if (!ensureOpen(future)) {
            return future;
        }

        return future.channel().bind(localAddress(), future).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
    }

    /**
     * Connect a {@link Channel} to the remote peer.
     */
    public ChannelFuture connect() {
        validate();
        Channel channel = factory().newChannel();
        return connect(channel.newFuture());
    }

    /**
     * See {@link #connect()}
     */
    public ChannelFuture connect(ChannelFuture future) {
        validate(future);
        if (remoteAddress == null) {
            throw new IllegalStateException("remoteAddress not set");
        }

        try {
            init(future.channel());
        } catch (Throwable t) {
            future.setFailure(t);
            return future;
        }

        if (!ensureOpen(future)) {
            return future;
        }

        if (localAddress() == null) {
            future.channel().connect(remoteAddress, future);
        } else {
            future.channel().connect(remoteAddress, localAddress(), future);
        }
        return future.addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
    }

    @SuppressWarnings("unchecked")
    private void init(Channel channel) throws Exception {
        if (channel.isActive()) {
            throw new IllegalStateException("channel already active:: " + channel);
        }
        if (channel.isRegistered()) {
            throw new IllegalStateException("channel already registered: " + channel);
        }
        if (!channel.isOpen()) {
            throw new ClosedChannelException();
        }

        ChannelPipeline p = channel.pipeline();
        p.addLast(handler());

        for (Entry<ChannelOption<?>, Object> e: options().entrySet()) {
            try {
                if (!channel.config().setOption((ChannelOption<Object>) e.getKey(), e.getValue())) {
                    logger.warn("Unknown channel option: " + e);
                }
            } catch (Throwable t) {
                logger.warn("Failed to set a channel option: " + channel, t);
            }
        }

        group().register(channel).syncUninterruptibly();
    }

    @Override
    protected void validate() {
        super.validate();
        if (handler() == null) {
            throw new IllegalStateException("handler not set");
        }
    }

    /**
     * Create a new {@link Bootstrap} using this "full-setup" {@link Bootstrap} as template.
     * Only the given parameters are replaced, the rest is configured exactly the same way as the template.
     */
    public Bootstrap newBootstrap(SocketAddress localAddress, SocketAddress remoteAddress, ChannelHandler handler) {
        validate();
        Bootstrap cb = new Bootstrap().handler(handler).channelFactory(factory()).group(group())
                .localAddress(localAddress).remoteAddress(remoteAddress);
        cb.options().putAll(options());
        return cb;
    }

    /**
     * Create a new {@link Bootstrap} using this "full-setup" {@link Bootstrap} as template.
     * Only the given parameters are replaced, the rest is configured exactly the same way as the template.
     */
    public Bootstrap newBootstrap(SocketAddress localAddress, SocketAddress remoteAddress) {
        return newBootstrap(localAddress, remoteAddress, handler());
    }
}
