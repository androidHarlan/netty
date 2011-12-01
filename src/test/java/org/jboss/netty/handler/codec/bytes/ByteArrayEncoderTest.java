/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.codec.bytes;

import static org.hamcrest.core.Is.is;
import static org.jboss.netty.buffer.ChannelBuffers.wrappedBuffer;
import static org.junit.Assert.assertThat;

import java.util.Random;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.handler.codec.embedder.EncoderEmbedder;
import org.junit.Before;
import org.junit.Test;

/**
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author Tomasz Blachowicz (tblachowicz@gmail.com)
 */
public class ByteArrayEncoderTest {

    private EncoderEmbedder<ChannelBuffer> embedder;

    @Before
    public void setUp() {
        embedder = new EncoderEmbedder<ChannelBuffer>(new ByteArrayEncoder());
    }

    @Test
    public void testDecode() {
        byte[] b = new byte[2048];
        new Random().nextBytes(b);
        embedder.offer(b);
        assertThat(embedder.poll(), is(wrappedBuffer(b)));
    }
    
    @Test
    public void testDecodeEmpty() {
        byte[] b = new byte[0];
        embedder.offer(b);
        assertThat(embedder.poll(), is(wrappedBuffer(b)));
    }

    @Test
    public void testDecodeOtherType() {
        String str = "Meep!";
        embedder.offer(str);
        assertThat(embedder.poll(), is((Object) str));
    }

}
