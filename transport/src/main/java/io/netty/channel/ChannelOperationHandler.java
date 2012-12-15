/*
 * Copyright 2012 The Netty Project
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
package io.netty.channel;

import java.net.SocketAddress;

public interface ChannelOperationHandler extends ChannelHandler {
    void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelFuture future) throws Exception;
    void connect(
            ChannelHandlerContext ctx, SocketAddress remoteAddress,
            SocketAddress localAddress, ChannelFuture future) throws Exception;
    void disconnect(ChannelHandlerContext ctx, ChannelFuture future) throws Exception;
    void close(ChannelHandlerContext ctx, ChannelFuture future) throws Exception;
    void deregister(ChannelHandlerContext ctx, ChannelFuture future) throws Exception;
    void flush(ChannelHandlerContext ctx, ChannelFuture future) throws Exception;
    void sendFile(ChannelHandlerContext ctx, FileRegion region, ChannelFuture future) throws Exception;

}
