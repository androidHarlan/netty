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
package io.netty.channel.local;

import io.netty.channel.EventExecutor;
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.channel.ChannelTaskScheduler;

import java.util.concurrent.ThreadFactory;

/**
 * {@link MultithreadEventLoopGroup} which must be used for the local transport.
 */
public class LocalEventLoopGroup extends MultithreadEventLoopGroup {

    /**
     * Create a new instance which used {@link #DEFAULT_POOL_SIZE} number of Threads
     */
    public LocalEventLoopGroup() {
        this(0);
    }

    /**
     * Create a new instance
     *
     * @param nThreads          the number of Threads to use or {@code 0} for the default of {@link #DEFAULT_POOL_SIZE}
     */
    public LocalEventLoopGroup(int nThreads) {
        this(nThreads, null);
    }

    /**
     * Create a new instance
     *
     * @param nThreads          the number of Threads to use or {@code 0} for the default of {@link #DEFAULT_POOL_SIZE}
     * @param threadFactory     the {@link ThreadFactory} or {@code null} to use the default
     */
    public LocalEventLoopGroup(int nThreads, ThreadFactory threadFactory) {
        super(nThreads, threadFactory);
    }

    @Override
    protected EventExecutor newChild(
            ThreadFactory threadFactory, ChannelTaskScheduler scheduler, Object... args) throws Exception {
        return new LocalEventLoop(this, threadFactory, scheduler);
    }
}
