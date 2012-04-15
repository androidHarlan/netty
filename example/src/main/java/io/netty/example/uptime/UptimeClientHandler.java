/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.example.uptime;

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.ClientBootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelStateEvent;
import io.netty.channel.ExceptionEvent;
import io.netty.channel.SimpleChannelUpstreamHandler;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;

/**
 * Keep reconnecting to the server while printing out the current uptime and
 * connection attempt status.
 */
public class UptimeClientHandler extends SimpleChannelUpstreamHandler {
    
    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(UptimeClientHandler.class);

    final ClientBootstrap bootstrap;
    private final Timer timer;
    private long startTime = -1;

    public UptimeClientHandler(ClientBootstrap bootstrap, Timer timer) {
        this.bootstrap = bootstrap;
        this.timer = timer;
    }

    InetSocketAddress getRemoteAddress() {
        return (InetSocketAddress) bootstrap.getOption("remoteAddress");
    }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) {
        println("Disconnected from: " + getRemoteAddress());
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) {
        println("Sleeping for: " + UptimeClient.RECONNECT_DELAY + "s");
        timer.newTimeout(new TimerTask() {
            public void run(Timeout timeout) throws Exception {
                println("Reconnecting to: " + getRemoteAddress());
                bootstrap.connect();
            }
        }, UptimeClient.RECONNECT_DELAY, TimeUnit.SECONDS);
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) {
        if (startTime < 0) {
            startTime = System.currentTimeMillis();
        }

        println("Connected to: " + getRemoteAddress());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
        Throwable cause = e.getCause();
        if (cause instanceof ConnectException) {
            startTime = -1;
            println("Failed to connect: " + cause.getMessage());
        }
        if (cause instanceof ReadTimeoutException) {
            // The connection was OK but there was no traffic for last period.
            println("Disconnecting due to no inbound traffic");
        } else {
            cause.printStackTrace();
        }
        ctx.getChannel().close();
    }

    void println(String msg) {
        if (startTime < 0) {
            logger.error(String.format("[SERVER IS DOWN] %s%n", msg));
        } else {
            logger.error(String.format("[UPTIME: %5ds] %s%n", (System.currentTimeMillis() - startTime) / 1000, msg));
        }
    }
}
