/*
 * Copyright 2016 The Netty Project
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
package io.netty5.channel.kqueue;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.buffer.BufferAllocator;
import io.netty5.channel.ChannelOption;

public class KQueueRcvAllocatorOverrideSocketSslEchoTest extends KQueueSocketSslEchoTest {
    @Override
    protected void configure(ServerBootstrap bootstrap, Bootstrap bootstrap2,
                             BufferAllocator bufferAllocator) {
        super.configure(bootstrap, bootstrap2, bufferAllocator);
        bootstrap.option(ChannelOption.READ_HANDLE_FACTORY, new KQueueReadHandleFactory());
        bootstrap.childOption(ChannelOption.READ_HANDLE_FACTORY, new KQueueReadHandleFactory());
        bootstrap2.option(ChannelOption.READ_HANDLE_FACTORY, new KQueueReadHandleFactory());
    }
}
