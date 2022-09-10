/*
 * Copyright 2022 The Netty Project
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
package io.netty5.handler.codec;

import io.netty5.buffer.Buffer;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageToByteEncoderTest {

    @Test
    void testAcceptOutboundMessage() throws Exception {
        TestEncoder encoder = new TestEncoder();
        assertTrue(encoder.acceptOutboundMessage("test"));
        assertFalse(encoder.acceptOutboundMessage(5));
    }

    @Test
    void testEncoderException() {
        EmbeddedChannel channel = new EmbeddedChannel(new MessageToByteEncoder<String>() {

            @Override
            protected Buffer allocateBuffer(ChannelHandlerContext ctx, String msg) {
                return ctx.bufferAllocator().allocate(0);
            }

            @Override
            protected void encode(ChannelHandlerContext ctx, String msg, Buffer out) {
                throw new EncoderException();
            }
        });
        assertThrows(EncoderException.class, () -> channel.writeOutbound("test"));
    }

    @Test
    void testException() {
        EmbeddedChannel channel = new EmbeddedChannel(new MessageToByteEncoder<String>() {

            @Override
            protected Buffer allocateBuffer(ChannelHandlerContext ctx, String msg) {
                return ctx.bufferAllocator().allocate(0);
            }

            @Override
            protected void encode(ChannelHandlerContext ctx, String msg, Buffer out) throws Exception {
                throw new Exception();
            }
        });
        assertThrows(EncoderException.class, () -> channel.writeOutbound("test"));
    }

    @Test
    void testWrite() {
        EmbeddedChannel channel = new EmbeddedChannel(new TestEncoder());

        channel.writeOutbound("test");
        Object o = channel.readOutbound();
        assertInstanceOf(Buffer.class, o);
        try (Buffer buffer = (Buffer) o) {
            assertEquals("test", buffer.toString(Charset.defaultCharset()));
        }

        Object msg = new Object();
        channel.writeOutbound(msg);
        o = channel.readOutbound();
        assertNotNull(o);
        assertSame(msg, o);
    }

    static final class TestEncoder extends MessageToByteEncoder<String> {

        TestEncoder() {
            super();
        }

        @Override
        protected Buffer allocateBuffer(ChannelHandlerContext ctx, String msg) {
            return ctx.bufferAllocator().allocate(16);
        }

        @Override
        protected void encode(ChannelHandlerContext ctx, String msg, Buffer out) {
            out.writeCharSequence(msg, Charset.defaultCharset());
        }
    }
}
