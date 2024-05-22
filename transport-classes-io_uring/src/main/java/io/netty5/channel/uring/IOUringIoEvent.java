/*
 * Copyright 2024 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.channel.uring;

import io.netty5.channel.IoEvent;

/**
 * {@link IoEvent} that will be produced as an result of a {@link IOUringIoOps}.
 */
public final class IOUringIoEvent implements IoEvent {

    private int id;
    private byte opcode;
    private int res;
    private int flags;
    private short data;

    /**
     * Create a new instance
     *
     * @param res       the result.
     * @param flags     the flags
     * @param id        the id
     * @param opcode    the op code
     * @param data      the user data that was given as part of the submission.
     */
    public IOUringIoEvent(int res, int flags, int id, byte opcode, short data) {
        this.res = res;
        this.flags = flags;
        this.id = id;
        this.opcode = opcode;
        this.data = data;
    }

    // Used internally to reduce object creation
    void update(int res, int flags, int id, byte opcode, short data) {
        this.res = res;
        this.flags = flags;
        this.id = id;
        this.opcode = opcode;
        this.data = data;
    }

    /**
     * Returns the result.
     *
     * @return  the result
     */
    public int res() {
        return res;
    }

    /**
     * Returns the flags.
     *
     * @return flags
     */
    public int flags() {
        return flags;
    }

    /**
     * Returns the id.
     *
     * @return id
     */
    public int id() {
        return id;
    }

    /**
     * Returns the op code of the {@link IOUringIoOps}.
     *
     * @return  opcode
     */
    public byte opcode() {
        return opcode;
    }

    /**
     * Returns the data that is passed as part of {@link IOUringIoOps}.
     *
     * @return  data.
     */
    public short data() {
        return data;
    };

    @Override
    public String toString() {
        return "IOUringIoEvent{" +
                "opcode=" + opcode +
                ", res=" + res +
                ", flags=" + flags +
                ", data=" + data +
                '}';
    }
}
