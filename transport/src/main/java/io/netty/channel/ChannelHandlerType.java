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
package io.netty.channel;

/**
 * Define the type of a {@link ChannelHandler}
 *
 */
public enum ChannelHandlerType {
    STATE(0),
    INBOUND(0),
    OPERATION(1),
    OUTBOUND(1);

    final int direction; // 0 - up (inbound), 1 - down (outbound)

    ChannelHandlerType(int direction) {
        if (direction != 0 && direction != 1) {
            throw new IllegalArgumentException("direction must be either 0 or 1");
        }
        this.direction = direction;
    }
}
