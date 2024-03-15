/*
 * Copyright 2023 The Netty Project
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
package io.netty.handler.codec.compression;

import com.github.luben.zstd.ZstdInputStreamNoFinalizer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.util.internal.ObjectUtil;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import static io.netty.handler.codec.compression.ZstdConstants.DEFAULT_MAX_BLOCK_SIZE;

/**
 * Decompresses a compressed block {@link ByteBuf} using the Zstandard algorithm.
 * See <a href="https://facebook.github.io/zstd">Zstandard</a>.
 */
public final class ZstdDecoder extends ByteToMessageDecoder {
    private final int maxBlockSize;
    private final MutableByteBufInputStream inputStream = new MutableByteBufInputStream();
    private ZstdInputStreamNoFinalizer zstdIs;

    private volatile State currentState = State.DECOMPRESS_DATA;

    /**
     * Creates a new Zstd decoder.
     *
     * Please note that if you use the default constructor, the MAX_BLOCK_SIZE
     * will be used. If you want to specify MAX_BLOCK_SIZE yourself,
     * please use {@link ZstdDecoder(int)} constructor
     */
    public ZstdDecoder() {
        this(DEFAULT_MAX_BLOCK_SIZE);
    }

    /**
     * Creates a new Zstd decoder.
     *  @param  maxBlockSize
     *            specifies the max block size
     */
    public ZstdDecoder(int maxBlockSize) {
        this.maxBlockSize = ObjectUtil.checkPositive(maxBlockSize, "maxBlockSize");
    }

    /**
     * Current state of stream.
     */
    private enum State {
        DECOMPRESS_DATA,
        CORRUPTED
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (currentState == State.CORRUPTED) {
            in.skipBytes(in.readableBytes());
            return;
        }
        try {
            final int compressedLength = in.readableBytes();
            if (compressedLength > maxBlockSize) {
                in.skipBytes(compressedLength);
                throw new TooLongFrameException("too large message: " + compressedLength + " bytes");
            }

            inputStream.current = in;

            ByteBuf outBuffer = null;
            try {
                int w = -1;
                do {
                    // Let's start with the compressedLength * 2 as often we will not have everything
                    // we need in the in buffer and don't want to reserve too much memory.
                    outBuffer = ctx.alloc().heapBuffer(compressedLength * 2);
                    byte[] array = outBuffer.array();
                    int writerOffset = outBuffer.arrayOffset() + outBuffer.writerIndex();
                    int writableBytes = outBuffer.writableBytes();
                    int written = 0;
                    while (writableBytes > 0 && (w = zstdIs.read(array, writerOffset, writableBytes)) != -1) {
                        writerOffset += w;
                        writableBytes -= w;
                        written += w;
                    }
                    outBuffer.writerIndex(outBuffer.writerIndex() + written);
                    if (outBuffer.isReadable()) {
                        out.add(outBuffer);
                    } else {
                        outBuffer.release();
                    }
                    outBuffer = null;
                } while (w != -1);
            } finally {
                if (outBuffer != null) {
                    outBuffer.release();
                }
            }
        } catch (Exception e) {
            currentState = State.CORRUPTED;
            throw new DecompressionException(e);
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
        zstdIs = new ZstdInputStreamNoFinalizer(inputStream);
        zstdIs.setContinuous(true);
    }

    @Override
    protected void handlerRemoved0(ChannelHandlerContext ctx) throws Exception {
        try {
            closeSilently(zstdIs);
        } finally {
            super.handlerRemoved0(ctx);
        }
    }

    private static void closeSilently(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignore) {
                // ignore
            }
        }
    }

    private static final class MutableByteBufInputStream extends InputStream {
        ByteBuf current;

        @Override
        public int read() {
            if (current == null || !current.isReadable()) {
                return -1;
            }
            return current.readByte() & 0xff;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            int available = available();
            if (available == 0) {
                return -1;
            }

            len = Math.min(available, len);
            current.readBytes(b, off, len);
            return len;
        }

        @Override
        public int available() {
            return current == null ? 0 : current.readableBytes();
        }
    }
}
