/*
 * Copyright 2021 The Netty Project
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
package io.netty.buffer.api.bytebuffer;

import io.netty.buffer.api.AllocationType;
import io.netty.buffer.api.AllocatorControl;
import io.netty.buffer.api.Buffer;
import io.netty.buffer.api.Drop;
import io.netty.buffer.api.MemoryManager;
import io.netty.buffer.api.StandardAllocationTypes;
import io.netty.buffer.api.internal.Statics;

import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;

import static io.netty.buffer.api.internal.Statics.bbslice;
import static io.netty.buffer.api.internal.Statics.convert;

public class ByteBufferMemoryManager implements MemoryManager {
    @Override
    public Buffer allocateShared(AllocatorControl allocatorControl, long size, Drop<Buffer> drop, Cleaner cleaner,
                                 AllocationType allocationType) {
        int capacity = Math.toIntExact(size);
        final ByteBuffer buffer;
        if (allocationType == StandardAllocationTypes.OFF_HEAP) {
            buffer = ByteBuffer.allocateDirect(capacity);
        } else if (allocationType == StandardAllocationTypes.ON_HEAP) {
            buffer = ByteBuffer.allocate(capacity);
        } else {
            throw new IllegalArgumentException("Unknown allocation type: " + allocationType);
        }
        return new NioBuffer(buffer, buffer, allocatorControl, convert(drop));
    }

    @Override
    public Buffer allocateConstChild(Buffer readOnlyConstParent) {
        assert readOnlyConstParent.readOnly();
        NioBuffer buf = (NioBuffer) readOnlyConstParent;
        return new NioBuffer(buf);
    }

    @Override
    public Drop<Buffer> drop() {
        return Statics.NO_OP_DROP;
    }

    @Override
    public Object unwrapRecoverableMemory(Buffer buf) {
        return ((NioBuffer) buf).recoverable();
    }

    @Override
    public Buffer recoverMemory(AllocatorControl allocatorControl, Object recoverableMemory, Drop<Buffer> drop) {
        ByteBuffer memory = (ByteBuffer) recoverableMemory;
        return new NioBuffer(memory, memory, allocatorControl, convert(drop));
    }

    @Override
    public Object sliceMemory(Object memory, int offset, int length) {
        var buffer = (ByteBuffer) memory;
        return bbslice(buffer, offset, length);
    }

    @Override
    public String implementationName() {
        return "ByteBuffer";
    }
}
