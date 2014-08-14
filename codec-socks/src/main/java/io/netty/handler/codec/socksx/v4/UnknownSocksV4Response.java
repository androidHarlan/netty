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
package io.netty.handler.codec.socksx.v4;

import io.netty.buffer.ByteBuf;

/**
 * An unknown socks response.
 *
 * @see SocksV4CmdResponseDecoder
 */
public final class UnknownSocksV4Response extends SocksV4Response {

    public UnknownSocksV4Response() {
    }

    @Override
    public void encodeAsByteBuf(ByteBuf byteBuf) {
        // NOOP
    }

    private static class UnknownSocksV4ResponseHolder {
        public static final UnknownSocksV4Response HOLDER_INSTANCE = new UnknownSocksV4Response();
    }

    public static UnknownSocksV4Response getInstance() {
        return UnknownSocksV4ResponseHolder.HOLDER_INSTANCE;
    }
}
