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
package io.netty5.handler.codec.frame;

import io.netty5.buffer.api.Buffer;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.handler.codec.DecoderException;
import io.netty5.handler.codec.DelimiterBasedFrameDecoder;
import io.netty5.handler.codec.Delimiters;
import io.netty5.handler.codec.TooLongFrameException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class DelimiterBasedFrameDecoderTest {

    @Test
    public void testFailSlowTooLongFrameRecovery() {
        EmbeddedChannel ch = new EmbeddedChannel(
                new DelimiterBasedFrameDecoder(1, true, false, Delimiters.nulDelimiter()));

        for (int i = 0; i < 2; i ++) {
            ch.writeInbound(ch.bufferAllocator().copyOf(new byte[] { 1, 2 }));
            try {
                assertTrue(ch.writeInbound(ch.bufferAllocator().copyOf(new byte[] { 0 })));
                fail(DecoderException.class.getSimpleName() + " must be raised.");
            } catch (TooLongFrameException e) {
                // Expected
            }

            ch.writeInbound(ch.bufferAllocator().copyOf(new byte[] { 'A', 0 }));
            try (Buffer buf = ch.readInbound()) {
                assertEquals("A", buf.toString(StandardCharsets.ISO_8859_1));
            }
        }
    }

    @Test
    public void testFailFastTooLongFrameRecovery() {
        EmbeddedChannel ch = new EmbeddedChannel(
                new DelimiterBasedFrameDecoder(1, Delimiters.nulDelimiter()));

        for (int i = 0; i < 2; i ++) {
            try {
                assertTrue(ch.writeInbound(ch.bufferAllocator().copyOf(new byte[] { 1, 2 })));
                fail(DecoderException.class.getSimpleName() + " must be raised.");
            } catch (TooLongFrameException e) {
                // Expected
            }

            ch.writeInbound(ch.bufferAllocator().copyOf(new byte[] { 0, 'A', 0 }));
            try (Buffer buf = ch.readInbound()) {
                assertEquals("A", buf.toString(StandardCharsets.ISO_8859_1));
            }
        }
    }
}
