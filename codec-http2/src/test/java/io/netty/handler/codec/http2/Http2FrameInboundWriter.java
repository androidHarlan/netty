/*
 * Copyright 2018 The Netty Project
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

package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import java.net.SocketAddress;

/**
 * Utility class which allows easy writing of HTTP2 frames via {@link EmbeddedChannel#writeInbound(Object...)}.
 */
final class Http2FrameInboundWriter {

    private final ChannelHandlerContext ctx;
    private final Http2FrameWriter writer;

    Http2FrameInboundWriter(EmbeddedChannel channel) {
        this(channel, new DefaultHttp2FrameWriter());
    }

    Http2FrameInboundWriter(EmbeddedChannel channel, Http2FrameWriter writer) {
        ctx = new WriteInboundChannelHandlerContext(channel);
        this.writer = writer;
    }

    void writeInboundData(int streamId, ByteBuf data, int padding, boolean endStream) {
        writer.writeData(ctx, streamId, data, padding, endStream, ctx.newPromise()).syncUninterruptibly();
    }

    void writeInboundHeaders(int streamId, Http2Headers headers,
                         int padding, boolean endStream) {
        writer.writeHeaders(ctx, streamId, headers, padding, endStream, ctx.newPromise()).syncUninterruptibly();
    }

    void writeInboundHeaders(int streamId, Http2Headers headers,
                               int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) {
        writer.writeHeaders(ctx, streamId, headers, streamDependency,
                weight, exclusive, padding, endStream, ctx.newPromise()).syncUninterruptibly();
    }

    void writeInboundPriority(int streamId, int streamDependency,
                                short weight, boolean exclusive) {
        writer.writePriority(ctx, streamId, streamDependency, weight,
                exclusive, ctx.newPromise()).syncUninterruptibly();
    }

    void writeInboundRstStream(int streamId, long errorCode) {
        writer.writeRstStream(ctx, streamId, errorCode, ctx.newPromise()).syncUninterruptibly();
    }

    void writeInboundSettings(Http2Settings settings) {
        writer.writeSettings(ctx, settings, ctx.newPromise()).syncUninterruptibly();
    }

    void writeInboundSettingsAck() {
        writer.writeSettingsAck(ctx, ctx.newPromise()).syncUninterruptibly();
    }

    void writeInboundPing(boolean ack, long data) {
        writer.writePing(ctx, ack, data, ctx.newPromise()).syncUninterruptibly();
    }

    void writePushPromise(int streamId, int promisedStreamId,
                                   Http2Headers headers, int padding) {
           writer.writePushPromise(ctx, streamId, promisedStreamId,
                   headers, padding, ctx.newPromise()).syncUninterruptibly();
    }

    void writeInboundGoAway(int lastStreamId, long errorCode, ByteBuf debugData) {
        writer.writeGoAway(ctx, lastStreamId, errorCode, debugData, ctx.newPromise()).syncUninterruptibly();
    }

    void writeInboundWindowUpdate(int streamId, int windowSizeIncrement) {
        writer.writeWindowUpdate(ctx, streamId, windowSizeIncrement, ctx.newPromise()).syncUninterruptibly();
    }

    void writeInboundFrame(byte frameType, int streamId,
                             Http2Flags flags, ByteBuf payload) {
        writer.writeFrame(ctx, frameType, streamId, flags, payload, ctx.newPromise()).syncUninterruptibly();
    }

    private static final class WriteInboundChannelHandlerContext
            implements ChannelHandlerContext, ChannelHandler {
        private final EmbeddedChannel channel;

        WriteInboundChannelHandlerContext(EmbeddedChannel channel) {
            this.channel = channel;
        }

        @Override
        public Channel channel() {
            return channel;
        }

        @Override
        public EventExecutor executor() {
            return channel.eventLoop();
        }

        @Override
        public String name() {
            return "WriteInbound";
        }

        @Override
        public ChannelHandler handler() {
            return this;
        }

        @Override
        public boolean isRemoved() {
            return false;
        }

        @Override
        public ChannelHandlerContext fireChannelRegistered() {
            channel.pipeline().fireChannelRegistered();
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelUnregistered() {
            channel.pipeline().fireChannelUnregistered();
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelActive() {
            channel.pipeline().fireChannelActive();
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelInactive() {
            channel.pipeline().fireChannelInactive();
            return this;
        }

        @Override
        public ChannelHandlerContext fireExceptionCaught(Throwable cause) {
            channel.pipeline().fireExceptionCaught(cause);
            return this;
        }

        @Override
        public ChannelHandlerContext fireUserEventTriggered(Object evt) {
            channel.pipeline().fireUserEventTriggered(evt);
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelRead(Object msg) {
            channel.pipeline().fireChannelRead(msg);
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelReadComplete() {
            channel.pipeline().fireChannelReadComplete();
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelWritabilityChanged() {
            channel.pipeline().fireChannelWritabilityChanged();
            return this;
        }

        @Override
        public ChannelHandlerContext read() {
            channel.read();
            return this;
        }

        @Override
        public ChannelHandlerContext flush() {
            channel.pipeline().fireChannelReadComplete();
            return this;
        }

        @Override
        public ChannelPipeline pipeline() {
            return channel.pipeline();
        }

        @Override
        public ByteBufAllocator alloc() {
            return channel.alloc();
        }

        @Override
        public <T> Attribute<T> attr(AttributeKey<T> key) {
            return channel.attr(key);
        }

        @Override
        public <T> boolean hasAttr(AttributeKey<T> key) {
            return channel.hasAttr(key);
        }

        @Override
        public Future<Void> bind(SocketAddress localAddress) {
            return channel.bind(localAddress);
        }

        @Override
        public Future<Void> connect(SocketAddress remoteAddress) {
            return channel.connect(remoteAddress);
        }

        @Override
        public Future<Void> connect(SocketAddress remoteAddress, SocketAddress localAddress) {
            return channel.connect(remoteAddress, localAddress);
        }

        @Override
        public Future<Void> disconnect() {
            return channel.disconnect();
        }

        @Override
        public Future<Void> close() {
            return channel.close();
        }

        @Override
        public Future<Void> register() {
            return channel.register();
        }

        @Override
        public Future<Void> deregister() {
            return channel.deregister();
        }

        @Override
        public ChannelHandlerContext bind(SocketAddress localAddress, Promise<Void> promise) {
            channel.bind(localAddress, promise);
            return this;
        }

        @Override
        public ChannelHandlerContext connect(SocketAddress remoteAddress, Promise<Void> promise) {
            channel.connect(remoteAddress, promise);
            return this;
        }

        @Override
        public ChannelHandlerContext connect(
                SocketAddress remoteAddress, SocketAddress localAddress, Promise<Void> promise) {
            channel.connect(remoteAddress, localAddress, promise);
            return this;
        }

        @Override
        public ChannelHandlerContext disconnect(Promise<Void> promise) {
            channel.disconnect(promise);
            return this;
        }

        @Override
        public ChannelHandlerContext close(Promise<Void> promise) {
            channel.close(promise);
            return this;
        }

        @Override
        public ChannelHandlerContext register(Promise<Void> promise) {
            channel.register(promise);
            return this;
        }

        @Override
        public ChannelHandlerContext deregister(Promise<Void> promise) {
            channel.deregister(promise);
            return this;
        }

        @Override
        public Future<Void> write(Object msg) {
            Promise<Void> promise = newPromise();
            write(msg, promise);
            return promise;
        }

        @Override
        public ChannelHandlerContext write(Object msg, Promise<Void> promise) {
            writeAndFlush(msg, promise);
            return this;
        }

        @Override
        public ChannelHandlerContext writeAndFlush(Object msg, Promise<Void> promise) {
            try {
                channel.writeInbound(msg);
                channel.runPendingTasks();
                promise.setSuccess(null);
            } catch (Throwable cause) {
                promise.setFailure(cause);
            }
            return this;
        }

        @Override
        public Future<Void> writeAndFlush(Object msg) {
            Promise<Void> promise = newPromise();
            writeAndFlush(msg, promise);
            return promise;
        }

        @Override
        public Promise<Void> newPromise() {
            return channel.newPromise();
        }

        @Override
        public Future<Void> newSucceededFuture() {
            return channel.newSucceededFuture();
        }

        @Override
        public Future<Void> newFailedFuture(Throwable cause) {
            return channel.newFailedFuture(cause);
        }
    }
}
