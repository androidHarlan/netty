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
package org.jboss.netty.handler.ssl;

import javax.net.ssl.SSLEngine;
import java.nio.ByteBuffer;

/**
 * A {@link ByteBuffer} pool dedicated for {@link SslHandler} performance
 * improvement.
 * <p>
 * In most cases, you won't need to create a new pool instance because
 * {@link SslHandler} has a default pool instance internally.
 * <p>
 * The reason why {@link SslHandler} requires a buffer pool is because the
 * current {@link SSLEngine} implementation always requires a 17KiB buffer for
 * every 'wrap' and 'unwrap' operation.  In most cases, the actual size of the
 * required buffer is much smaller than that, and therefore allocating a 17KiB
 * buffer for every 'wrap' and 'unwrap' operation wastes a lot of memory
 * bandwidth, resulting in the application performance degradation.
 */
public class SslBufferPool {

    private static final int DEFAULT_POOL_SIZE = OpenSslEngine.MAX_ENCRYPTED_PACKET_LENGTH * 1024;

    private final ByteBuffer[] pool;
    private final int maxBufferCount;
    private int index;

    /**
     * Creates a new buffer pool whose size is {@code 18113536}, which can
     * hold {@code 1024} buffers.
     */
    public SslBufferPool() {
        this(DEFAULT_POOL_SIZE);
    }

    /**
     * Creates a new buffer pool.
     *
     * @param maxPoolSize the maximum number of bytes that this pool can hold
     */
    public SslBufferPool(int maxPoolSize) {
        if (maxPoolSize <= 0) {
            throw new IllegalArgumentException("maxPoolSize: " + maxPoolSize);
        }

        int maxBufferCount = maxPoolSize / OpenSslEngine.MAX_ENCRYPTED_PACKET_LENGTH;
        if (maxPoolSize % OpenSslEngine.MAX_ENCRYPTED_PACKET_LENGTH != 0) {
            maxBufferCount ++;
        }

        pool = new ByteBuffer[maxBufferCount];
        this.maxBufferCount = maxBufferCount;
    }

    /**
     * Returns the maximum size of this pool in byte unit.  The returned value
     * can be somewhat different from what was specified in the constructor.
     */
    public int getMaxPoolSize() {
        return maxBufferCount * OpenSslEngine.MAX_ENCRYPTED_PACKET_LENGTH;
    }

    /**
     * Returns the number of bytes which were allocated but have not been
     * acquired yet.  You can estimate how optimal the specified maximum pool
     * size is from this value.  If it keeps returning {@code 0}, it means the
     * pool is getting exhausted.  If it keeps returns a unnecessarily big
     * value, it means the pool is wasting the heap space.
     */
    public synchronized int getUnacquiredPoolSize() {
        return index * OpenSslEngine.MAX_ENCRYPTED_PACKET_LENGTH;
    }

    /**
     * Acquire a new {@link ByteBuffer} out of the {@link SslBufferPool}
     *
     */
    public synchronized ByteBuffer acquireBuffer() {
        if (index == 0) {
            return ByteBuffer.allocate(OpenSslEngine.MAX_ENCRYPTED_PACKET_LENGTH);
        } else {
            return (ByteBuffer) pool[-- index].clear();
        }
    }

    /**
     * Will get removed. Please use {@link #acquireBuffer()}
     *
     */
    @Deprecated
    ByteBuffer acquire() {
        return acquireBuffer();
    }

    /**
     * Release a previous acquired {@link ByteBuffer}
     */
    public synchronized void releaseBuffer(ByteBuffer buffer) {
        if (index < maxBufferCount) {
            pool[index ++] = buffer;
        }
    }

    /**
     * @deprecated Use {@link #releaseBuffer(ByteBuffer)}
     */
    @Deprecated
    void release(ByteBuffer buffer) {
        releaseBuffer(buffer);
    }
}
