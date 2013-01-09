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
package io.netty.handler.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.MessageBuf;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundByteHandler;
import io.netty.channel.ChannelOutboundMessageHandler;
import io.netty.channel.ChannelPromise;

public abstract class ByteToMessageCodec<I> extends ChannelHandlerAdapter
        implements ChannelInboundByteHandler, ChannelOutboundMessageHandler<I> {

    private final MessageToByteEncoder<I> encoder =
            new MessageToByteEncoder<I>() {
        @Override
        public void encode(
                ChannelHandlerContext ctx,
                Object msg, ByteBuf out) throws Exception {
            ByteToMessageCodec.this.encode(ctx, msg, out);
        }
    };

    private final ByteToMessageDecoder decoder =
            new ByteToMessageDecoder() {
        @Override
        public Object decode(
                ChannelHandlerContext ctx, ByteBuf in) throws Exception {
            return ByteToMessageCodec.this.decode(ctx, in);
        }
    };

    @Override
    public ByteBuf newInboundBuffer(
            ChannelHandlerContext ctx) throws Exception {
        return decoder.newInboundBuffer(ctx);
    }

    @Override
    public void inboundBufferUpdated(ChannelHandlerContext ctx) throws Exception {
        decoder.inboundBufferUpdated(ctx);
    }

    @Override
    public MessageBuf<I> newOutboundBuffer(
            ChannelHandlerContext ctx) throws Exception {
        return encoder.newOutboundBuffer(ctx);
    }

    @Override
    public void flush(
            ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        encoder.flush(ctx, promise);
    }

    @Override
    public void freeInboundBuffer(ChannelHandlerContext ctx) throws Exception {
        decoder.freeInboundBuffer(ctx);
    }

    @Override
    public void freeOutboundBuffer(ChannelHandlerContext ctx) throws Exception {
        encoder.freeOutboundBuffer(ctx);
    }

    protected abstract void encode(
            ChannelHandlerContext ctx,
            Object msg, ByteBuf out) throws Exception;

    protected abstract Object decode(
            ChannelHandlerContext ctx, ByteBuf in) throws Exception;
}
