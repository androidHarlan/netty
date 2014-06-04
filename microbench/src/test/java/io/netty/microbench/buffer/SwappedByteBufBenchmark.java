/*
* Copyright 2014 The Netty Project
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
package io.netty.microbench.buffer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.SwappedByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.GenerateMicroBenchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.nio.ByteOrder;

@State(Scope.Benchmark)
@Warmup(iterations = 25)
@Measurement(iterations = 50)
public class SwappedByteBufBenchmark extends AbstractMicrobenchmark {
    private final ByteBuf buffer = Unpooled.directBuffer(8);
    private final ByteBuf swappedByteBuf = new SwappedByteBuf(buffer);
    private final ByteBuf unsafeSwappedByteBuf = buffer.order(ByteOrder.LITTLE_ENDIAN);

    public SwappedByteBufBenchmark() {
        if (unsafeSwappedByteBuf.getClass().equals(SwappedByteBuf.class)) {
            throw new IllegalStateException("Should not use " + SwappedByteBuf.class.getSimpleName());
        }
    }

    @Param({ "00000", "00256", "01024", "04096", "16384", "65536" })
    public int intSize;

    @Param({ "00000", "00256", "01024", "04096", "16384", "65536" })
    public long longSize;

    @GenerateMicroBenchmark
    public void swappedByteBufSetInt() {
        swappedByteBuf.setLong(0, intSize);
    }

    @GenerateMicroBenchmark
    public void swappedByteBufSetShort() {
        swappedByteBuf.setShort(0, intSize);
    }

    @GenerateMicroBenchmark
    public void swappedByteBufSetLong() {
        swappedByteBuf.setLong(0, longSize);
    }

    @GenerateMicroBenchmark
    public void unsafeSwappedByteBufSetInt() {
        unsafeSwappedByteBuf.setInt(0, intSize);
    }

    @GenerateMicroBenchmark
    public void unsafeSwappedByteBufSetShort() {
        unsafeSwappedByteBuf.setShort(0, intSize);
    }

    @GenerateMicroBenchmark
    public void unsafeSwappedByteBufSetLong() {
        unsafeSwappedByteBuf.setLong(0, longSize);
    }

}
