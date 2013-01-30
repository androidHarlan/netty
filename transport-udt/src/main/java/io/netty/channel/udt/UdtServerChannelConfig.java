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
package io.netty.channel.udt;

import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelOption;

import com.barchart.udt.OptionUDT;
import com.barchart.udt.TypeUDT;
import com.barchart.udt.nio.KindUDT;

/**
 * A {@link ChannelConfig} for a {@link UdtServerChannel}.
 * <p>
 * Note that {@link TypeUDT#DATAGRAM} message oriented channels treat
 * {@code "receiveBufferSize"} and {@code "sendBufferSize"} as maximum message
 * size. If received or sent message does not fit specified sizes,
 * {@link ChannelException} will be thrown.
 */
public interface UdtServerChannelConfig extends UdtChannelConfig {

    /**
     * Gets {@link KindUDT#ACCEPTOR} channel backlog via {@link ChannelOption#SO_BACKLOG}.
     */
    int getBacklog();

    /**
     * Sets {@link KindUDT#ACCEPTOR} channel backlog via {@link ChannelOption#SO_BACKLOG}.
     */
    UdtServerChannelConfig setBacklog(int backlog);

    /**
     * Sets {@link OptionUDT#Protocol_Receive_Buffer_Size}
     */
    UdtServerChannelConfig setProtocolReceiveBufferSize(int size);

    /**
     * Sets {@link OptionUDT#Protocol_Send_Buffer_Size}
     */
    UdtServerChannelConfig setProtocolSendBufferSize(int size);

    /**
     * Sets the {@link ChannelOption#SO_RCVBUF} option.
     */
    UdtServerChannelConfig setReceiveBufferSize(int receiveBufferSize);

    /**
     * Sets the {@link ChannelOption#SO_REUSEADDR} option.
     */
    UdtServerChannelConfig setReuseAddress(boolean reuseAddress);

    /**
     * Sets the {@link ChannelOption#SO_SNDBUF} option.
     */
    UdtServerChannelConfig setSendBufferSize(int sendBufferSize);

    /**
     * Sets the {@link ChannelOption#SO_LINGER} option.
     */
    UdtServerChannelConfig setSoLinger(int soLinger);

    /**
     * Sets {@link OptionUDT#System_Receive_Buffer_Size}
     */
    UdtServerChannelConfig setSystemReceiveBufferSize(int size);

    /**
     * Sets {@link OptionUDT#System_Send_Buffer_Size}
     */
    UdtServerChannelConfig setSystemSendBufferSize(int size);

}
