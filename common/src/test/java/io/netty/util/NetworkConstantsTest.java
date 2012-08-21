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
package io.netty.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import org.junit.Test;

public class NetworkConstantsTest {

    @Test
    public void testLocalhost() throws UnknownHostException {
        assertNotNull(NetworkConstants.LOCALHOST);
        assertSame(NetworkConstants.LOCALHOST, InetAddress.getLocalHost());
    }

    @Test
    public void testLoopback() throws UnknownHostException {
        assertNotNull(NetworkConstants.LOOPBACK_IF);
    }

}
