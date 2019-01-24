/*
 * Copyright 2014 The Netty Project

 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:

 * http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel;

import java.net.SocketAddress;

import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class AbstractChannelTest {

    @Test
    public void ensureInitialRegistrationFiresActive() throws Throwable {
        EventLoop eventLoop = mock(EventLoop.class);
        // This allows us to have a single-threaded test
        when(eventLoop.inEventLoop()).thenReturn(true);
        when(eventLoop.unsafe()).thenReturn(mock(EventLoop.Unsafe.class));

        TestChannel channel = new TestChannel(eventLoop);
        ChannelInboundHandler handler = mock(ChannelInboundHandler.class);
        channel.pipeline().addLast(handler);

        registerChannel(channel);

        verify(handler).handlerAdded(any(ChannelHandlerContext.class));
        verify(handler).channelRegistered(any(ChannelHandlerContext.class));
        verify(handler).channelActive(any(ChannelHandlerContext.class));
    }

    @Test
    public void ensureSubsequentRegistrationDoesNotFireActive() throws Throwable {
        final EventLoop eventLoop = mock(EventLoop.class);
        // This allows us to have a single-threaded test
        when(eventLoop.inEventLoop()).thenReturn(true);
        when(eventLoop.unsafe()).thenReturn(mock(EventLoop.Unsafe.class));

        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                ((Runnable) invocationOnMock.getArgument(0)).run();
                return null;
            }
        }).when(eventLoop).execute(any(Runnable.class));

        final TestChannel channel = new TestChannel(eventLoop);
        ChannelInboundHandler handler = mock(ChannelInboundHandler.class);

        channel.pipeline().addLast(handler);

        registerChannel(channel);
        channel.unsafe().deregister(new DefaultChannelPromise(channel));

        registerChannel(channel);

        verify(handler).handlerAdded(any(ChannelHandlerContext.class));

        // Should register twice
        verify(handler,  times(2)) .channelRegistered(any(ChannelHandlerContext.class));
        verify(handler).channelActive(any(ChannelHandlerContext.class));
        verify(handler).channelUnregistered(any(ChannelHandlerContext.class));
    }

    @Test
    public void ensureDefaultChannelId() {
        final EventLoop eventLoop = mock(EventLoop.class);
        TestChannel channel = new TestChannel(eventLoop);
        final ChannelId channelId = channel.id();
        assertTrue(channelId instanceof DefaultChannelId);
    }

    private static void registerChannel(Channel channel) throws Exception {
        DefaultChannelPromise future = new DefaultChannelPromise(channel);
        channel.register(future);
        future.sync(); // Cause any exceptions to be thrown
    }

    private static class TestChannel extends AbstractChannel {
        private static final ChannelMetadata TEST_METADATA = new ChannelMetadata(false);
        private class TestUnsafe extends AbstractUnsafe {

            @Override
            public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) { }
        }

        TestChannel(EventLoop eventLoop) {
            super(null, eventLoop);
        }

        @Override
        public ChannelConfig config() {
            return new DefaultChannelConfig(this);
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public ChannelMetadata metadata() {
            return TEST_METADATA;
        }

        @Override
        protected AbstractUnsafe newUnsafe() {
            return new TestUnsafe();
        }

        @Override
        protected SocketAddress localAddress0() {
            return null;
        }

        @Override
        protected SocketAddress remoteAddress0() {
            return null;
        }

        @Override
        protected void doBind(SocketAddress localAddress) throws Exception { }

        @Override
        protected void doDisconnect() throws Exception { }

        @Override
        protected void doClose() throws Exception { }

        @Override
        protected void doBeginRead() throws Exception { }

        @Override
        protected void doWrite(ChannelOutboundBuffer in) throws Exception { }
    }
}
