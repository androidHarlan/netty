/*
 * Copyright 2016 The Netty Project
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

package io.netty.handler.codec.haproxy;

import io.netty.handler.codec.haproxy.HAProxySSLTLV.Client;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.*;

public class HAProxySSLTLVTest {

    @Test
    public void testClientBitmask() throws Exception {

        // 0b0000_0111
        final byte allClientsEnabled = 0x7;
        final HAProxySSLTLV allClientsEnabledTLV =
                new HAProxySSLTLV(0, allClientsEnabled, Collections.<HAProxyTLV>emptyList(), new byte[0]);

        assertEquals(3, allClientsEnabledTLV.clients().size());
        assertEquals(true, allClientsEnabledTLV.clients().contains(Client.PP2_CLIENT_CERT_CONN));
        assertEquals(true, allClientsEnabledTLV.clients().contains(Client.PP2_CLIENT_SSL));
        assertEquals(true, allClientsEnabledTLV.clients().contains(Client.PP2_CLIENT_CERT_SESS));

        // 0b0000_0101
        final byte clientSSLandClientCertSessEnabled = 0x5;

        final HAProxySSLTLV clientSSLandClientCertSessTLV =
                new HAProxySSLTLV(0, clientSSLandClientCertSessEnabled, Collections.<HAProxyTLV>emptyList(),
                                  new byte[0]);

        assertEquals(2, clientSSLandClientCertSessTLV.clients().size());
        assertEquals(false, clientSSLandClientCertSessTLV.clients().contains(Client.PP2_CLIENT_CERT_CONN));
        assertEquals(true, clientSSLandClientCertSessTLV.clients().contains(Client.PP2_CLIENT_SSL));
        assertEquals(true, clientSSLandClientCertSessTLV.clients().contains(Client.PP2_CLIENT_CERT_SESS));

        // 0b0000_0000
        final byte noClientEnabled = 0x0;

        final HAProxySSLTLV noClientTlv =
                new HAProxySSLTLV(0, noClientEnabled, Collections.<HAProxyTLV>emptyList(),
                                  new byte[0]);

        assertEquals(0, noClientTlv.clients().size());
    }
}
