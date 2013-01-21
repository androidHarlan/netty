/*
 * Copyright 2013 The Netty Project
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
package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import static org.junit.Assert.*;

public class SnappyFramedDecoderTest {
    private final SnappyFramedDecoder decoder = new SnappyFramedDecoder();

    @Test(expected = CompressionException.class)
    public void testReservedUnskippableChunkTypeCausesError() throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
            0x03, 0x01, 0x00, 0x00
        });

        decoder.decode(null, in, null);
    }

    @Test(expected = CompressionException.class)
    public void testInvalidStreamIdentifierLength() throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
            -0x80, 0x05, 0x00, 'n', 'e', 't', 't', 'y'
        });

        decoder.decode(null, in, null);
    }

    @Test(expected = CompressionException.class)
    public void testInvalidStreamIdentifierValue() throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
            -0x80, 0x06, 0x00, 's', 'n', 'e', 't', 't', 'y'
        });

        decoder.decode(null, in, null);
    }

    @Test(expected = CompressionException.class)
    public void testReservedSkippableBeforeStreamIdentifier() throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
            -0x7f, 0x06, 0x00, 's', 'n', 'e', 't', 't', 'y'
        });

        decoder.decode(null, in, null);
    }

    @Test(expected = CompressionException.class)
    public void testUncompressedDataBeforeStreamIdentifier() throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
            0x01, 0x05, 0x00, 'n', 'e', 't', 't', 'y'
        });

        decoder.decode(null, in, null);
    }

    @Test(expected = CompressionException.class)
    public void testCompressedDataBeforeStreamIdentifier() throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
            0x00, 0x05, 0x00, 'n', 'e', 't', 't', 'y'
        });

        decoder.decode(null, in, null);
    }

    @Test
    public void testReservedSkippableSkipsInput() throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
           -0x80, 0x06, 0x00, 0x73, 0x4e, 0x61, 0x50, 0x70, 0x59,
           -0x7f, 0x05, 0x00, 'n', 'e', 't', 't', 'y'
        });

        ByteBuf out = Unpooled.unmodifiableBuffer(Unpooled.EMPTY_BUFFER);

        decoder.decode(null, in, out);

        assertEquals(17, in.readerIndex());
    }

    @Test
    public void testUncompressedDataAppendsToOut() throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
           -0x80, 0x06, 0x00, 0x73, 0x4e, 0x61, 0x50, 0x70, 0x59,
           0x01, 0x05, 0x00, 0x00, 0x00, 0x00, 0x00, 'n', 'e', 't', 't', 'y'
        });

        ByteBuf out = Unpooled.buffer(5);

        decoder.decode(null, in, out);

        byte[] expected = {
            'n', 'e', 't', 't', 'y'
        };
        assertArrayEquals(expected, out.array());
    }

    @Test
    public void testCompressedDataDecodesAndAppendsToOut() throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
           -0x80, 0x06, 0x00, 0x73, 0x4e, 0x61, 0x50, 0x70, 0x59,
           0x00, 0x07, 0x00, 0x00, 0x00, 0x00, 0x00,
                 0x05, // preamble length
                 0x04 << 2, // literal tag + length
                 0x6e, 0x65, 0x74, 0x74, 0x79 // "netty"
        });

        ByteBuf out = Unpooled.buffer(5);

        decoder.decode(null, in, out);

        byte[] expected = {
            'n', 'e', 't', 't', 'y'
        };
        assertArrayEquals(expected, out.array());
    }
}
