/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.spdy;

import io.netty.buffer.ChannelBuffer;
import io.netty.util.internal.DetectionUtil;

abstract class SpdyHeaderBlockCompressor {

    static SpdyHeaderBlockCompressor newInstance(
            int version, int compressionLevel, int windowBits, int memLevel) {

        if (DetectionUtil.javaVersion() >= 7) {
            return new SpdyHeaderBlockZlibCompressor(
                    version, compressionLevel);
        } else {
            return new SpdyHeaderBlockJZlibCompressor(
                    version, compressionLevel, windowBits, memLevel);
        }
    }

    abstract void setInput(ChannelBuffer decompressed);
    abstract void encode(ChannelBuffer compressed);
    abstract void end();
}
