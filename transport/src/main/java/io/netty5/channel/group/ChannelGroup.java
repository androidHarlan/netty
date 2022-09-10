/*
 * Copyright 2013 The Netty Project
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
package io.netty5.channel.group;

import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.buffer.Buffer;
import io.netty5.buffer.DefaultBufferAllocators;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelId;
import io.netty5.channel.EventLoop;
import io.netty5.channel.ServerChannel;
import io.netty5.util.concurrent.GlobalEventExecutor;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * A thread-safe {@link Set} that contains open {@link Channel}s and provides
 * various bulk operations on them.  Using {@link ChannelGroup}, you can
 * categorize {@link Channel}s into a meaningful group (e.g. on a per-service
 * or per-state basis.)  A closed {@link Channel} is automatically removed from
 * the collection, so that you don't need to worry about the life cycle of the
 * added {@link Channel}.  A {@link Channel} can belong to more than one
 * {@link ChannelGroup}.
 *
 * <h3>Broadcast a message to multiple {@link Channel}s</h3>
 * <p>
 * If you need to broadcast a message to more than one {@link Channel}, you can
 * add the {@link Channel}s associated with the recipients and call {@link ChannelGroup#write(Object)}:
 * <pre>
 * <strong>{@link ChannelGroup} recipients =
 *         new {@link DefaultChannelGroup}({@link GlobalEventExecutor}.INSTANCE);</strong>
 * recipients.add(channelA);
 * recipients.add(channelB);
 * ..
 * <strong>recipients.write({@link DefaultBufferAllocators#preferredAllocator()}.copyOf(
 *         "Service will shut down for maintenance in 5 minutes.",
 *         {@link StandardCharsets#UTF_8}));</strong>
 * </pre>
 *
 * <h3>Simplify shutdown process with {@link ChannelGroup}</h3>
 * <p>
 * If both {@link ServerChannel}s and non-{@link ServerChannel}s exist in the
 * same {@link ChannelGroup}, any requested I/O operations on the group are
 * performed for the {@link ServerChannel}s first and then for the others.
 * <p>
 * This rule is very useful when you shut down a server in one shot:
 *
 * <pre>
 * <strong>{@link ChannelGroup} allChannels =
 *         new {@link DefaultChannelGroup}({@link GlobalEventExecutor}.INSTANCE);</strong>
 *
 * public static void main(String[] args) throws Exception {
 *     {@link ServerBootstrap} b = new {@link ServerBootstrap}(..);
 *     ...
 *     b.childHandler(new MyHandler());
 *
 *     // Start the server
 *     b.getPipeline().addLast("handler", new MyHandler());
 *     {@link Channel} serverChannel = b.bind(..).sync();
 *     <strong>allChannels.add(serverChannel);</strong>
 *
 *     ... Wait until the shutdown signal reception ...
 *
 *     // Close the serverChannel and then all accepted connections.
 *     <strong>allChannels.close().await();</strong>
 * }
 *
 * public class MyHandler implements {@link ChannelHandler} {
 *     {@code @Override}
 *     public void channelActive({@link ChannelHandlerContext} ctx) {
 *         // closed on shutdown.
 *         <strong>allChannels.add(ctx.channel());</strong>
 *         ctx.fireChannelActive();
 *     }
 * }
 * </pre>
 */
public interface ChannelGroup extends Set<Channel>, Comparable<ChannelGroup> {

    /**
     * Returns the name of this group.  A group name is purely for helping
     * you to distinguish one group from others.
     */
    String name();

    /**
     * Returns the {@link Channel} which has the specified {@link ChannelId}.
     *
     * @return the matching {@link Channel} if found. {@code null} otherwise.
     */
    Channel find(ChannelId id);

    /**
     * Writes the specified {@code message} to all {@link Channel}s in this
     * group.
     * If the specified {@code message} is an instance of {@link Buffer}, it is automatically
     * {@linkplain Buffer#copy() copied} to avoid a race condition.
     * Please note that this operation is asynchronous as {@link Channel#write(Object)} is.
     *
     * @return itself
     */
    ChannelGroupFuture write(Object message);

    /**
     * Writes the specified {@code message} to all {@link Channel}s in this
     * group that are matched by the given {@link ChannelMatcher}.
     * If the specified {@code message} is an instance of {@link Buffer}, it is automatically
     * {@linkplain Buffer#copy() copied} to avoid a race condition.
     * Please note that this operation is asynchronous as {@link Channel#write(Object)} is.
     *
     * @return the {@link ChannelGroupFuture} instance that notifies when
     *         the operation is done for all channels
     */
    ChannelGroupFuture write(Object message, ChannelMatcher matcher);

    /**
     * Flush all {@link Channel}s in this
     * group.
     * If the specified {@code message} is an instance of {@link Buffer}, it is automatically
     * {@linkplain Buffer#copy() copied} to avoid a race condition.
     * Please note that this operation is asynchronous as {@link Channel#flush()} is.
     *
     * @return the {@link ChannelGroupFuture} instance that notifies when
     *         the operation is done for all channels
     */
    ChannelGroup flush();

    /**
     * Flush all {@link Channel}s in this group that are matched by the given {@link ChannelMatcher}.
     * If the specified {@code message} is an instance of {@link Buffer}, it is automatically
     * {@linkplain Buffer#copy() copied} to avoid a race condition.
     * Please note that this operation is asynchronous as {@link Channel#flush()} is.
     *
     * @return the {@link ChannelGroupFuture} instance that notifies when
     *         the operation is done for all channels
     */
    ChannelGroup flush(ChannelMatcher matcher);

    /**
     * Shortcut for calling {@link #write(Object)} and {@link #flush()}.
     */
    ChannelGroupFuture writeAndFlush(Object message);

    /**
     * @deprecated Use {@link #writeAndFlush(Object)} instead.
     */
    @Deprecated
    ChannelGroupFuture flushAndWrite(Object message);

    /**
     * Shortcut for calling {@link #write(Object)} and {@link #flush()} and only act on
     * {@link Channel}s that are matched by the {@link ChannelMatcher}.
     */
    ChannelGroupFuture writeAndFlush(Object message, ChannelMatcher matcher);

    /**
     * @deprecated Use {@link #writeAndFlush(Object, ChannelMatcher)} instead.
     */
    @Deprecated
    ChannelGroupFuture flushAndWrite(Object message, ChannelMatcher matcher);

    /**
     * Disconnects all {@link Channel}s in this group from their remote peers.
     *
     * @return the {@link ChannelGroupFuture} instance that notifies when
     *         the operation is done for all channels
     */
    ChannelGroupFuture disconnect();

    /**
     * Disconnects all {@link Channel}s in this group from their remote peers,
     * that are matched by the given {@link ChannelMatcher}.
     *
     * @return the {@link ChannelGroupFuture} instance that notifies when
     *         the operation is done for all channels
     */
    ChannelGroupFuture disconnect(ChannelMatcher matcher);

    /**
     * Closes all {@link Channel}s in this group.  If the {@link Channel} is
     * connected to a remote peer or bound to a local address, it is
     * automatically disconnected and unbound.
     *
     * @return the {@link ChannelGroupFuture} instance that notifies when
     *         the operation is done for all channels
     */
    ChannelGroupFuture close();

    /**
     * Closes all {@link Channel}s in this group that are matched by the given {@link ChannelMatcher}.
     * If the {@link Channel} is  connected to a remote peer or bound to a local address, it is
     * automatically disconnected and unbound.
     *
     * @return the {@link ChannelGroupFuture} instance that notifies when
     *         the operation is done for all channels
     */
    ChannelGroupFuture close(ChannelMatcher matcher);

    /**
     * @deprecated This method will be removed in the next major feature release.
     *
     * Deregister all {@link Channel}s in this group from their {@link EventLoop}.
     * Please note that this operation is asynchronous as {@link Channel#deregister()} is.
     *
     * @return the {@link ChannelGroupFuture} instance that notifies when
     *         the operation is done for all channels
     */
    @Deprecated
    ChannelGroupFuture deregister();

    /**
     * @deprecated This method will be removed in the next major feature release.
     *
     * Deregister all {@link Channel}s in this group from their {@link EventLoop} that are matched by the given
     * {@link ChannelMatcher}. Please note that this operation is asynchronous as {@link Channel#deregister()} is.
     *
     * @return the {@link ChannelGroupFuture} instance that notifies when
     *         the operation is done for all channels
     */
    @Deprecated
    ChannelGroupFuture deregister(ChannelMatcher matcher);

    /**
     * Returns the {@link ChannelGroupFuture} which will be notified when all {@link Channel}s that are part of this
     * {@link ChannelGroup}, at the time of calling, are closed.
     */
    ChannelGroupFuture newCloseFuture();

    /**
     * Returns the {@link ChannelGroupFuture} which will be notified when all {@link Channel}s that are part of this
     * {@link ChannelGroup}, at the time of calling, are closed.
     */
    ChannelGroupFuture newCloseFuture(ChannelMatcher matcher);
}
