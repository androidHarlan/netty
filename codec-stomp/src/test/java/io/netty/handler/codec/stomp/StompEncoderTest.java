/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.stomp;

import io.netty.util.CharsetUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Assert;

public class StompEncoderTest {

    private EmbeddedChannel channel;

    @Before
    public void setup() throws Exception {
        channel = new EmbeddedChannel(new StompEncoder());
    }

    @After
    public void teardown() throws Exception {
        Assert.assertFalse(channel.finish());
    }

    @Test
    public void testFrameAndContentEncoding() {
        StompFrame frame = new DefaultStompFrame(StompCommand.CONNECT);
        StompHeaders headers = frame.headers();
        headers.set(StompHeaders.ACCEPT_VERSION, "1.1,1.2");
        headers.set(StompHeaders.HOST, "stomp.github.org");
        channel.writeOutbound(frame);
        channel.writeOutbound(LastStompContent.EMPTY_LAST_CONTENT);
        ByteBuf aggregatedBuffer = Unpooled.buffer();
        ByteBuf byteBuf = channel.readOutbound();
        Assert.assertNotNull(byteBuf);
        aggregatedBuffer.writeBytes(byteBuf);

        byteBuf = channel.readOutbound();
        Assert.assertNotNull(byteBuf);
        aggregatedBuffer.writeBytes(byteBuf);
        aggregatedBuffer.resetReaderIndex();
        String content = aggregatedBuffer.toString(CharsetUtil.UTF_8);
        Assert.assertEquals(StompTestConstants.CONNECT_FRAME, content);
    }

}
