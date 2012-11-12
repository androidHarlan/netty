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
package io.netty.codec.socks;

import io.netty.channel.embedded.EmbeddedByteChannel;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import sun.net.util.IPAddressUtil;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
public class SocksCmdRequestDecoderTest {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(SocksCmdRequestDecoderTest.class);

    private void testSocksCmdRequestDecoderWithDifferentParams(SocksMessage.CmdType cmdType, SocksMessage.AddressType addressType, String host, int port) {
        logger.debug("Testing cmdType: " + cmdType + " addressType: " + addressType + " host: " + host + " port: " + port);
        SocksCmdRequest msg = new SocksCmdRequest(cmdType, addressType, host, port);
        SocksCmdRequestDecoder decoder = new SocksCmdRequestDecoder();
        EmbeddedByteChannel embedder = new EmbeddedByteChannel(decoder);
        SocksCommonTestUtils.writeMessageIntoEmbedder(embedder, msg);
        if (msg.getAddressType() == SocksMessage.AddressType.UNKNOWN) {
            assertTrue(embedder.readInbound() instanceof UnknownSocksRequest);
        } else {
            msg = (SocksCmdRequest) embedder.readInbound();
            assertTrue(msg.getCmdType().equals(cmdType));
            assertTrue(msg.getAddressType().equals(addressType));
            assertTrue(msg.getHost().equals(host));
            assertTrue(msg.getPort() == port);
        }
        assertNull(embedder.readInbound());
    }

    @Test
    public void testCmdRequestDecoderIPv4() {
        String[] hosts = {"127.0.0.1",};
        int[] ports = {0, 32769, 65535 };
        for (SocksMessage.CmdType cmdType : SocksMessage.CmdType.values()) {
            for (String host : hosts) {
                for (int port : ports) {
                    testSocksCmdRequestDecoderWithDifferentParams(cmdType, SocksMessage.AddressType.IPv4, host, port);
                }
            }
        }
    }

    @Test
    public void testCmdRequestDecoderIPv6() {
        String[] hosts = {SocksCommonUtils.ipv6toStr(IPAddressUtil.textToNumericFormatV6("::1"))};
        int[] ports = {0, 32769, 65535};
        for (SocksMessage.CmdType cmdType : SocksMessage.CmdType.values()) {
            for (String host : hosts) {
                for (int port : ports) {
                    testSocksCmdRequestDecoderWithDifferentParams(cmdType, SocksMessage.AddressType.IPv6, host, port);
                }
            }
        }
    }

    @Test
    public void testCmdRequestDecoderDomain() {
        String[] hosts = {"google.com"};
        int[] ports = {0, 32769, 65535};
        for (SocksMessage.CmdType cmdType : SocksMessage.CmdType.values()) {
            for (String host : hosts) {
                for (int port : ports) {
                    testSocksCmdRequestDecoderWithDifferentParams(cmdType, SocksMessage.AddressType.DOMAIN, host, port);
                }
            }
        }
    }

    @Test
    public void testCmdRequestDecoderUnknown() {
        String host = "google.com";
        int port = 80;
        for (SocksMessage.CmdType cmdType : SocksMessage.CmdType.values()) {
            testSocksCmdRequestDecoderWithDifferentParams(cmdType, SocksMessage.AddressType.UNKNOWN, host, port);
        }
    }
}
