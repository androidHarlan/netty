/*
 * Copyright 2024 The Netty Project
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
package io.netty.channel.uring;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class IoUringFileTest {

    private static EventLoopGroup group;

    @BeforeAll
    public static void loadJNI() {
        assumeTrue(IoUring.isAvailable());
        group =  new MultiThreadIoEventLoopGroup(1, IoUringIoHandler.newFactory());
    }

    @Test
    public void testGetFd() throws IOException {
        File file = File.createTempFile("temp", ".tmp");
        file.deleteOnExit();
        FileChannel channel = FileChannel.open(file.toPath());
        int fd = Native.getFd(channel);
        Assertions.assertTrue(fd > 0);
    }

    @AfterAll
    public static void closeResource() throws InterruptedException {
        group.shutdownGracefully().sync();
    }
}
