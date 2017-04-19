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
package io.netty.handler.codec.sockjs.protocol;

import org.junit.Test;

import static io.netty.util.CharsetUtil.UTF_8;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class FramesTest {

    @Test
    public void copy() {
        assertCopy(new MessageFrame("testing copy"));
        assertCopy(new CloseFrame(100, "msg"));
        assertCopy(new HeartbeatFrame());
        assertCopy(new OpenFrame());
        assertCopy(new PreludeFrame());
    }

    @Test
    public void duplicate() {
        assertDuplicate(new MessageFrame("testing duplicate"));
        assertDuplicate(new CloseFrame(101, "msg"));
        assertDuplicate(new HeartbeatFrame());
        assertDuplicate(new OpenFrame());
        assertDuplicate(new PreludeFrame());
    }

    @Test
    public void retain() {
        assertRetain(new MessageFrame("testing retain"));
        assertRetain(new CloseFrame(102, "msg"));
        assertRetainImmutable(new HeartbeatFrame());
        assertRetainImmutable(new OpenFrame());
        assertRetainImmutable(new PreludeFrame());
    }

    private static void assertCopy(final Frame frame) {
        final Frame copy = frame.copy();
        try {
            assertThat(asString(copy), equalTo(asString(frame)));
            assertThat(copy == frame, is(false));
        } finally {
            copy.release();
            frame.release();
        }
    }

    private static String asString(final Frame frame) {
        return frame.content().toString(UTF_8);
    }

    private static void assertDuplicate(final Frame frame) {
        final Frame duplicate = frame.duplicate();
        try {
            assertThat(asString(duplicate), equalTo(asString(frame)));
            assertThat(duplicate == frame, is(false));
        } finally {
            duplicate.release();
            frame.release();
        }
    }

    private static void assertRetain(final Frame frame) {
        assertThat(frame.refCnt(), is(1));
        assertThat(frame.retain().refCnt(), is(2));
        assertThat(frame.release(), is(false));
        assertThat(frame.release(), is(true));
    }

    private static void assertRetainImmutable(final Frame frame) {
        assertThat(frame.refCnt(), is(1));
        assertThat(frame.retain().refCnt(), is(1));
        assertThat(frame.release(), is(false));
    }
}
