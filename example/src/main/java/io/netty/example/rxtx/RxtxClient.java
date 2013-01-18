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
package io.netty.example.rxtx;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.rxtx.RxtxChannelOption;
import io.netty.channel.socket.oio.OioEventLoopGroup;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.channel.rxtx.RxtxChannel;
import io.netty.channel.rxtx.RxtxDeviceAddress;

/**
 * Sends one message to a serial device
 */
public final class RxtxClient {

    public static void main(String[] args) throws Exception {
        Bootstrap b = new Bootstrap();
        try {
            b.group(new OioEventLoopGroup())
             .channel(RxtxChannel.class)
             .remoteAddress(new RxtxDeviceAddress("/dev/tty.usbmodem641"))
             .option(RxtxChannelOption.BAUD_RATE, 57600)
             .option(RxtxChannelOption.WAIT_AFTER_CONNECT, 1500)
             .handler(new ChannelInitializer<RxtxChannel>() {
                 @Override
                 public void initChannel(RxtxChannel ch) throws Exception {
                     ch.pipeline().addLast(
                         new LineBasedFrameDecoder(32768),
                         new StringEncoder(),
                         new StringDecoder(),
                         new RxtxClientHandler()
                     );
                 }
             });

            ChannelFuture f = b.connect().sync();

            f.channel().closeFuture().sync();
        } finally {
            b.shutdown();
        }
    }

    private RxtxClient() {
    }
}
