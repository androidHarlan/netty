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
package io.netty5.microbench.websocket;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty5.handler.codec.http.websocketx.WebSocket13FrameEncoder;
import io.netty5.microbench.channel.EmbeddedChannelWriteReleaseHandlerContext;
import io.netty5.microbench.util.AbstractMicrobenchmark;
import io.netty5.util.concurrent.Future;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;

import java.util.concurrent.ThreadLocalRandom;

import static io.netty5.buffer.api.DefaultBufferAllocators.offHeapAllocator;
import static io.netty5.buffer.api.DefaultBufferAllocators.onHeapAllocator;

@State(Scope.Benchmark)
@Fork(value = 2)
@Threads(1)
@Warmup(iterations = 5)
@Measurement(iterations = 10)
public class WebSocketFrame13EncoderBenchmark extends AbstractMicrobenchmark {

    private WebSocket13FrameEncoder websocketEncoder;

    private ChannelHandlerContext context;

    private Buffer content;

    @Param({ "0", "2", "4", "8", "32", "100", "1000", "3000" })
    public int contentLength;

    @Param({ "true", "false" })
    public boolean pooledAllocator;

    @Param({ "true", "false" })
    public boolean masking;

    @Setup(Level.Trial)
    public void setUp() {
        byte[] bytes = new byte[contentLength];
        ThreadLocalRandom.current().nextBytes(bytes);
        BufferAllocator allocator = pooledAllocator? offHeapAllocator() : onHeapAllocator();
        content = allocator.allocate(contentLength).writeBytes(bytes).makeReadOnly();

        websocketEncoder = new WebSocket13FrameEncoder(masking);
        context = new EmbeddedChannelWriteReleaseHandlerContext(allocator, websocketEncoder) {
            @Override
            protected void handleException(Throwable t) {
                handleUnexpectedException(t);
            }
        };
    }

    @TearDown(Level.Trial)
    public void teardown() {
        content.close();
        content = null;
        context.close();
    }

    @Benchmark
    public Future<Void> writeWebSocketFrame() {
        return websocketEncoder.write(context, new BinaryWebSocketFrame(content.split()));
    }

    @Override
    protected ChainedOptionsBuilder newOptionsBuilder() throws Exception {
        return super.newOptionsBuilder().addProfiler(GCProfiler.class);
    }
}
