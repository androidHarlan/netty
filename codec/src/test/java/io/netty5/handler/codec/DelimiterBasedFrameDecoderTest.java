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
package io.netty5.handler.codec;

import io.netty5.buffer.api.Buffer;
import io.netty5.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static io.netty5.buffer.api.DefaultBufferAllocators.preferredAllocator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DelimiterBasedFrameDecoderTest {

    @Test
    public void testMultipleLinesStrippedDelimiters() {
        EmbeddedChannel ch = new EmbeddedChannel(new DelimiterBasedFrameDecoder(8192, true,
                Delimiters.lineDelimiter()));
        ch.writeInbound(ch.bufferAllocator().copyOf("TestLine\r\ng\r\n", Charset.defaultCharset()));

        try (Buffer buf = ch.readInbound()) {
            assertEquals("TestLine", buf.toString(Charset.defaultCharset()));
        }

        try (Buffer buf2 = ch.readInbound()) {
            assertEquals("g", buf2.toString(Charset.defaultCharset()));
        }

        assertNull(ch.readInbound());
        ch.finish();
    }

    @Test
    public void testIncompleteLinesStrippedDelimiters() {
        EmbeddedChannel ch = new EmbeddedChannel(new DelimiterBasedFrameDecoder(8192, true,
                Delimiters.lineDelimiter()));
        ch.writeInbound(ch.bufferAllocator().copyOf("Test", Charset.defaultCharset()));
        assertNull(ch.readInbound());
        ch.writeInbound(ch.bufferAllocator().copyOf("Line\r\ng\r\n", Charset.defaultCharset()));

        try (Buffer buf = ch.readInbound()) {
            assertEquals("TestLine", buf.toString(Charset.defaultCharset()));
        }

        try (Buffer buf2 = ch.readInbound()) {
            assertEquals("g", buf2.toString(Charset.defaultCharset()));
        }

        assertNull(ch.readInbound());
        ch.finish();
    }

    @Test
    public void testMultipleLines() {
        EmbeddedChannel ch = new EmbeddedChannel(new DelimiterBasedFrameDecoder(8192, false,
                Delimiters.lineDelimiter()));
        ch.writeInbound(ch.bufferAllocator().copyOf("TestLine\r\ng\r\n", Charset.defaultCharset()));

        try (Buffer buf = ch.readInbound()) {
            assertEquals("TestLine\r\n", buf.toString(Charset.defaultCharset()));
        }

        try (Buffer buf2 = ch.readInbound()) {
            assertEquals("g\r\n", buf2.toString(Charset.defaultCharset()));
        }

        assertNull(ch.readInbound());
        ch.finish();
    }

    @Test
    public void testIncompleteLines() {
        EmbeddedChannel ch = new EmbeddedChannel(new DelimiterBasedFrameDecoder(8192, false,
                Delimiters.lineDelimiter()));
        ch.writeInbound(ch.bufferAllocator().copyOf("Test", Charset.defaultCharset()));
        assertNull(ch.readInbound());
        ch.writeInbound(ch.bufferAllocator().copyOf("Line\r\ng\r\n", Charset.defaultCharset()));

        try (Buffer buf = ch.readInbound()) {
            assertEquals("TestLine\r\n", buf.toString(Charset.defaultCharset()));
        }

        try (Buffer buf2 = ch.readInbound()) {
            assertEquals("g\r\n", buf2.toString(Charset.defaultCharset()));
        }

        assertNull(ch.readInbound());
        ch.finish();
    }

    @Test
    public void testDecode() {
        EmbeddedChannel ch = new EmbeddedChannel(
                new DelimiterBasedFrameDecoder(8192, true, Delimiters.lineDelimiter()));

        ch.writeInbound(ch.bufferAllocator().copyOf("first\r\nsecond\nthird", StandardCharsets.US_ASCII));

        try (Buffer buf = ch.readInbound()) {
            assertEquals("first", buf.toString(StandardCharsets.US_ASCII));
        }

        try (Buffer buf2 = ch.readInbound()) {
            assertEquals("second", buf2.toString(StandardCharsets.US_ASCII));
        }

        assertNull(ch.readInbound());
        ch.finish();
    }

    @Test
    void testDelimitersClosed() {
        Buffer[] delimiters = Delimiters.lineDelimiter();
        new DelimiterBasedFrameDecoder(8192, false, delimiters);
        assertFalse(delimiters[0].isAccessible());
        assertFalse(delimiters[1].isAccessible());

        delimiters = Delimiters.nulDelimiter();
        new DelimiterBasedFrameDecoder(8192, false, delimiters);
        assertFalse(delimiters[0].isAccessible());
    }

    @Test
    void testEmptyDelimiter() {
        Buffer delimiter = preferredAllocator().allocate(0);
        assertThrows(IllegalArgumentException.class, () -> new DelimiterBasedFrameDecoder(8192, false, delimiter));
        assertFalse(delimiter.isAccessible());
    }

}
