/*
 * Copyright 2020 The Netty Project
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
package io.netty.handler.pcap;

import io.netty.buffer.ByteBuf;

public final class PcapHeaders {

    /**
     * Generate Pcap Global Header
     * @param byteBuf byteBuf ByteBuf where we'll write header data
     */
    public static ByteBuf generateGlobalHeader(ByteBuf byteBuf) {
        byteBuf.writeInt(0xa1b2c3d4); // magic_number
        byteBuf.writeShort(2);        // version_major
        byteBuf.writeShort(4);        // version_minor
        byteBuf.writeInt(0);          // thiszone
        byteBuf.writeInt(0);          // sigfigs
        byteBuf.writeInt(0xffff);     // snaplen
        byteBuf.writeInt(1);          // network
        return byteBuf;
    }

    /**
     * Generate Pcap Packet Header
     *
     * @param byteBuf ByteBuf where we'll write header data
     * @param ts_sec   timestamp seconds
     * @param ts_usec  timestamp microseconds
     * @param incl_len number of octets of packet saved in file
     * @param orig_len actual length of packet
     */
    public static ByteBuf generatePacketHeader(ByteBuf byteBuf, int ts_sec, int ts_usec, int incl_len, int orig_len) {
        byteBuf.writeInt(ts_sec);
        byteBuf.writeInt(ts_usec);
        byteBuf.writeInt(incl_len);
        byteBuf.writeInt(orig_len);
        return byteBuf;
    }
}
