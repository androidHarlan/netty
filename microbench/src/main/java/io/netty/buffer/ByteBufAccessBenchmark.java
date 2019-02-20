/*
* Copyright 2019 The Netty Project
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
package io.netty.buffer;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.internal.PlatformDependent;

@Warmup(iterations = 5, time = 1500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 1500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
public class ByteBufAccessBenchmark extends AbstractMicrobenchmark {

    static final class NioFacade extends WrappedByteBuf {
        private final ByteBuffer byteBuffer;
        NioFacade(ByteBuffer byteBuffer) {
            super(Unpooled.EMPTY_BUFFER);
            this.byteBuffer = byteBuffer;
        }
        @Override
        public ByteBuf setLong(int index, long value) {
            byteBuffer.putLong(index, value);
            return this;
        }
        @Override
        public long getLong(int index) {
            return byteBuffer.getLong(index);
        }
        @Override
        public byte readByte() {
            return byteBuffer.get();
        }
        @Override
        public ByteBuf touch() {
            // hack since WrappedByteBuf.readerIndex(int) is final
            byteBuffer.position(0);
            return this;
        }
        @Override
        public boolean release() {
            PlatformDependent.freeDirectBuffer(byteBuffer);
            return true;
        }
    }

    public enum ByteBufType {
        UNSAFE {
            @Override
            ByteBuf newBuffer() {
                return new UnpooledUnsafeDirectByteBuf(
                        UnpooledByteBufAllocator.DEFAULT, 64, 64).setIndex(0, 64);
            }
        },
        UNSAFE_SLICE {
            @Override
            ByteBuf newBuffer() {
                return UNSAFE.newBuffer().slice(16, 48);
            }
        },
        HEAP {
            @Override
            ByteBuf newBuffer() {
                return new UnpooledUnsafeHeapByteBuf(
                        UnpooledByteBufAllocator.DEFAULT, 64, 64).setIndex(0,  64);
            }
        },
        COMPOSITE {
            @Override
            ByteBuf newBuffer() {
                return Unpooled.wrappedBuffer(UNSAFE.newBuffer(), HEAP.newBuffer());
            }
        },
        NIO {
            @Override
            ByteBuf newBuffer() {
                return new NioFacade(ByteBuffer.allocateDirect(64));
            }
        };
        abstract ByteBuf newBuffer();
    }

    @Param
    public ByteBufType bufferType;

    private ByteBuf buffer;

    @Setup
    public void setup() {
        buffer = bufferType.newBuffer();
    }

    @TearDown
    public void tearDown() {
        buffer.release();
    }

    @Benchmark
    public long setGetLong() {
        return buffer.setLong(0, 1).getLong(0);
    }

    @Benchmark
    public ByteBuf setLong() {
        return buffer.setLong(0, 1);
    }

    @Benchmark
    public void readEightBytes(final Blackhole bh) {
        buffer.readerIndex(0).touch();
        bh.consume(buffer.readByte());
        bh.consume(buffer.readByte());
        bh.consume(buffer.readByte());
        bh.consume(buffer.readByte());
        bh.consume(buffer.readByte());
        bh.consume(buffer.readByte());
        bh.consume(buffer.readByte());
        bh.consume(buffer.readByte());
    }

    // doesn't seem to be possible to parameterize JVM args with JMH right now
//  @Test
//  public void runWithNoCheck() throws Exception {
//      new Runner(newOptionsBuilder()
//              .jvmArgsAppend("-Dio.netty.buffer.checkAccessible=false").build()).run();
//  }
}
