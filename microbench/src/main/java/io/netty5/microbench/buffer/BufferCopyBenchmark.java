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
package io.netty5.microbench.buffer;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class BufferCopyBenchmark extends AbstractMicrobenchmark {
    static {
        System.setProperty("io.netty5.buffer.checkAccessible", "false");
    }

    @Param({"7", "36", "128", "512" })
    private int size;
    @Param({"true", "false" })
    private boolean directByteBuff;
    @Param({"true", "false" })
    private boolean directByteBuffer;
    @Param({"false", "true" })
    private boolean readonlyByteBuffer;
    @Param({"true", "false" })
    private boolean pooledbuffer;
    @Param({"true", "false" })
    private boolean alignedCopyByteBuffer;
    @Param({"true", "false" })
    private boolean alignedCopyBuffer;
    @Param({"true", "false" })
    private boolean nativeOrderByteBuffer;

    private ByteBuffer byteBuffer;
    private Buffer buffer;
    private int index;

    @Setup
    public void setup() {
        final int requiredByteBufSize = alignedCopyBuffer ? size : size + 1;
        final int requiredByteBufferSize = alignedCopyByteBuffer ? size : size + 1;
        byteBuffer = directByteBuffer ?
                ByteBuffer.allocateDirect(requiredByteBufferSize) :
                ByteBuffer.allocate(requiredByteBufferSize);
        if (pooledbuffer) {
            buffer = directByteBuff ?
                    BufferAllocator.offHeapPooled().allocate(requiredByteBufSize) :
                    BufferAllocator.onHeapPooled().allocate(requiredByteBufSize);
        } else {
            buffer = directByteBuff ?
                    BufferAllocator.offHeapUnpooled().allocate(requiredByteBufSize) :
                    BufferAllocator.onHeapUnpooled().allocate(requiredByteBufSize);
        }
        if (!alignedCopyByteBuffer) {
            byteBuffer.position(1);
            byteBuffer = byteBuffer.slice();
        }
        if (readonlyByteBuffer) {
            byteBuffer = byteBuffer.asReadOnlyBuffer();
        }
        final ByteOrder byteBufferOrder;
        if (!nativeOrderByteBuffer) {
            byteBufferOrder = ByteOrder.LITTLE_ENDIAN == ByteOrder.nativeOrder() ?
                    ByteOrder.BIG_ENDIAN :
                    ByteOrder.LITTLE_ENDIAN;
        } else {
            byteBufferOrder = ByteOrder.nativeOrder();
        }
        byteBuffer.order(byteBufferOrder);
        index = alignedCopyBuffer ? 0 : 1;
    }

    @Benchmark
    public Buffer setBytes() {
        byteBuffer.clear();
        buffer.resetOffsets();
        return buffer.writeBytes(byteBuffer);
    }

    @TearDown
    public void tearDown() {
        buffer.close();
    }

}
