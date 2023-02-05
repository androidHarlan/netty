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
package io.netty5.buffer.tests;

import io.netty5.buffer.AllocationType;
import io.netty5.buffer.AllocatorControl;
import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferAllocator;
import io.netty5.buffer.Drop;
import io.netty5.buffer.MemoryManager;
import io.netty5.buffer.internal.InternalBufferUtils;
import io.netty5.util.Send;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static io.netty5.buffer.SensitiveBufferAllocator.sensitiveOffHeapAllocator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SensitiveBufferTest {
    @Test
    public void sensitiveBufferMustZeroMemoryOnClose() {
        MemoryManager baseMemoryManager = MemoryManager.instance();
        StubManager stubManager = new StubManager(baseMemoryManager);
        try (Buffer buffer = MemoryManager.using(stubManager, () -> sensitiveOffHeapAllocator().allocate(8))) {
            buffer.writeLong(0x0102030405060708L);
        }
        assertEquals(8, stubManager.getBytesCleared());
    }

    @Test
    public void sensitiveReadOnlyBufferMustZeroMemoryOnClose() {
        MemoryManager baseMemoryManager = MemoryManager.instance();
        StubManager stubManager = new StubManager(baseMemoryManager);
        try (Buffer buffer = MemoryManager.using(stubManager, () -> sensitiveOffHeapAllocator().allocate(8))) {
            buffer.writeLong(0x0102030405060708L);
            // We must be able to zero the sensitive memory even if the buffer becomes read-only!
            buffer.makeReadOnly();
        }
        assertEquals(8, stubManager.getBytesCleared());
    }

    @Test
    public void closingSplitPartMustNotZeroBytesUntilAllPartsAreClosed() {
        MemoryManager baseMemoryManager = MemoryManager.instance();
        StubManager stubManager = new StubManager(baseMemoryManager);
        try (Buffer buffer = MemoryManager.using(stubManager, () -> sensitiveOffHeapAllocator().allocate(8))) {
            buffer.writeLong(0x0102030405060708L);
            Buffer split = buffer.readSplit(4);
            split.close();
            // The four bytes from the split part we closed must not be zeroed because the drop has insufficient
            // information about the structural sharing.
            assertEquals(0, stubManager.getBytesCleared());
        }
        assertEquals(8, stubManager.getBytesCleared());
    }

    @Test
    public void closingReadOnlySplitPartMustNotZeroBytesUntilAllPartsAreClosed() {
        MemoryManager baseMemoryManager = MemoryManager.instance();
        StubManager stubManager = new StubManager(baseMemoryManager);
        try (Buffer buffer = MemoryManager.using(stubManager, () -> sensitiveOffHeapAllocator().allocate(8))) {
            buffer.writeLong(0x0102030405060708L);
            Buffer split = buffer.readSplit(4);
            split.makeReadOnly();
            split.close();
            // The four bytes from the split part we closed must not be zeroed because the drop has insufficient
            // information about the structural sharing.
            assertEquals(0, stubManager.getBytesCleared());
            buffer.makeReadOnly();
        }
        assertEquals(8, stubManager.getBytesCleared());
    }

    @Test
    public void closingBuffersWithComplicatedStructuralSharingMustNotZeroBytesUntilAllPartsAreClosed() {
        MemoryManager baseMemoryManager = MemoryManager.instance();
        StubManager stubManager = new StubManager(baseMemoryManager);
        try (Buffer buffer = MemoryManager.using(stubManager, () -> sensitiveOffHeapAllocator().allocate(8))) {
            buffer.writeLong(0x0102030405060708L).makeReadOnly();
            try (Buffer split = buffer.readSplit(4)) {
                assertTrue(split.readOnly());
                split.copy(true).close();
            }
            final Send<Buffer> send;
            try (Buffer copy = buffer.copy(true)) {
                send = buffer.send();
                copy.readSplit(2).close();
            }
            assertEquals(0, stubManager.getBytesCleared());
            send.close();
            assertEquals(8, stubManager.getBytesCleared());
        }
    }

    @Test
    public void sensitiveAllocatorMustCaptureMemoryManager() {
        MemoryManager baseMemoryManager = MemoryManager.instance();
        StubManager stubManager = new StubManager(baseMemoryManager);

        try (BufferAllocator allocator = MemoryManager.using(stubManager, () -> sensitiveOffHeapAllocator())) {
            allocator.allocate(8).close();
            assertThat(stubManager.getBytesCleared()).isEqualTo(8);
        }

        try (BufferAllocator allocator = sensitiveOffHeapAllocator()) {
            allocator.allocate(8).close();
            assertThat(stubManager.getBytesCleared()).isEqualTo(8); // No change, stub not captured here.
        }

        try (BufferAllocator allocator = sensitiveOffHeapAllocator()) {
            MemoryManager.using(stubManager, () -> allocator.allocate(8)).close();
            assertThat(stubManager.getBytesCleared()).isEqualTo(16); // Stub captured on allocation, as override.
        }
    }

    private static final class StubManager implements MemoryManager {
        private final MemoryManager baseMemoryManager;
        private final AtomicInteger bytesCleared = new AtomicInteger();

        StubManager(MemoryManager baseMemoryManager) {
            this.baseMemoryManager = baseMemoryManager;
        }

        @Override
        public Buffer allocateShared(AllocatorControl control, long size,
                                     Function<Drop<Buffer>, Drop<Buffer>> dropDecorator,
                                     AllocationType allocationType) {
            return baseMemoryManager.allocateShared(
                    control,
                    size,
                    drop -> dropDecorator.apply(new CheckingDrop(bytesCleared, baseMemoryManager, drop)),
                    allocationType);
        }

        @Override
        public Buffer allocateConstChild(Buffer readOnlyConstParent) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object unwrapRecoverableMemory(Buffer buf) {
            return baseMemoryManager.unwrapRecoverableMemory(buf);
        }

        @Override
        public Buffer recoverMemory(AllocatorControl control, Object recoverableMemory, Drop<Buffer> drop) {
            return baseMemoryManager.recoverMemory(control, recoverableMemory, drop);
        }

        @Override
        public Object sliceMemory(Object memory, int offset, int length) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clearMemory(Object memory) {
            baseMemoryManager.clearMemory(memory);
        }

        @Override
        public int sizeOf(Object memory) {
            return baseMemoryManager.sizeOf(memory);
        }

        @Override
        public String implementationName() {
            throw new UnsupportedOperationException();
        }

        public int getBytesCleared() {
            return bytesCleared.get();
        }

        private static final class CheckingDrop implements Drop<Buffer> {
            private final AtomicInteger bytesCleared;
            private final MemoryManager manager;
            private final Drop<Buffer> delegate;

            CheckingDrop(AtomicInteger bytesCleared, MemoryManager manager, Drop<Buffer> delegate) {
                this.bytesCleared = bytesCleared;
                this.manager = manager;
                this.delegate = delegate;
            }

            @Override
            public void drop(Buffer obj) {
                Object memory = manager.unwrapRecoverableMemory(obj);
                try (Buffer buf = manager.recoverMemory(() -> null, memory, InternalBufferUtils.NO_OP_DROP)) {
                    int capacity = buf.capacity();
                    for (int i = 0; i < capacity; i++) {
                        assertEquals((byte) 0, buf.getByte(i));
                    }
                    bytesCleared.addAndGet(capacity);
                }
                delegate.drop(obj);
            }

            @Override
            public Drop<Buffer> fork() {
                // CheckingDrop should be guarded by an ArcDrop.
                throw new UnsupportedOperationException();
            }

            @Override
            public void attach(Buffer obj) {
                delegate.attach(obj);
            }

            @Override
            public String toString() {
                return "CheckingDrop(" + delegate + ')';
            }
        }
    }
}
