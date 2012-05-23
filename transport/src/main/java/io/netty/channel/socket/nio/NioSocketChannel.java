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
package io.netty.channel.socket.nio;

import io.netty.channel.Channel;
import io.netty.channel.ChannelCloseFuture;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelSink;
import io.netty.channel.socket.SocketChannels;

import java.net.SocketAddress;
import java.nio.channels.SocketChannel;

public abstract class NioSocketChannel extends AbstractNioChannel implements io.netty.channel.socket.SocketChannel {

    private static final int ST_OPEN = 0;
    private static final int ST_BOUND = 1;
    private static final int ST_CONNECTED = 2;
    private static final int ST_CLOSED = -1;
    volatile int state = ST_OPEN;
    volatile boolean inputClosed;
    volatile boolean outputClosed;

    private final NioSocketChannelConfig config;
    private final ChannelCloseFuture inputCloseFuture = new ChannelCloseFuture(this);
    private final ChannelCloseFuture outputCloseFuture = new ChannelCloseFuture(this);

    public NioSocketChannel(
            Channel parent, ChannelFactory factory,
            ChannelPipeline pipeline, ChannelSink sink,
            SocketChannel socket, NioWorker worker) {
        super(parent, factory, pipeline, sink, worker, new NioSocketJdkChannel(socket));

        config = new DefaultNioSocketChannelConfig(socket.socket());
    }


    @Override
    public ChannelFuture closeInput() {
        return SocketChannels.closeInput(this);
    }

    @Override
    public ChannelFuture closeOutput() {
        return SocketChannels.closeOutput(this);
    }
    
    @Override
    public NioWorker getWorker() {
        return (NioWorker) super.getWorker();
    }

    @Override
    public NioSocketChannelConfig getConfig() {
        return config;
    }

    @Override
    public boolean isOpen() {
        return state >= ST_OPEN;
    }

    @Override
    public boolean isBound() {
        return state >= ST_BOUND;
    }

    @Override
    public boolean isConnected() {
        return state == ST_CONNECTED;
    }

    final void setBound() {
        assert state == ST_OPEN : "Invalid state: " + state;
        state = ST_BOUND;
    }

    final void setConnected() {
        if (state != ST_CLOSED) {
            state = ST_CONNECTED;
        }
    }

    @Override
    protected boolean setClosed() {
        state = ST_CLOSED;
        return super.setClosed();
    }
    

    @Override
    public ChannelFuture write(Object message, SocketAddress remoteAddress) {
        if (remoteAddress == null || remoteAddress.equals(getRemoteAddress())) {
            return super.write(message, null);
        } else {
            return getUnsupportedOperationFuture();
        }
    }


    @Override
    public ChannelFuture getCloseInputFuture() {
        return inputCloseFuture;
    }


    @Override
    public ChannelFuture getCloseOutputFuture() {
        return outputCloseFuture;
    }
    
    boolean setClosedInput() {
        inputClosed = true;
        return inputCloseFuture.setClosed();
    }
    
    boolean setClosedOutput() {
        outputClosed = true;
        return outputCloseFuture.setClosed();
    }
    
    @Override
    protected NioSocketJdkChannel getJdkChannel() {
        return (NioSocketJdkChannel) super.getJdkChannel();
    }


    @Override
    public boolean isInputOpen() {
        return isOpen() && !inputClosed;
    }


    @Override
    public boolean isOutputOpen() {
        return isOpen() && !outputClosed;

    }

}
