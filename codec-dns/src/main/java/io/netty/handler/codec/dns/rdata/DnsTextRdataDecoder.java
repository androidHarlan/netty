/*
 * Copyright 2019 The Netty Project
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

package io.netty.handler.codec.dns.rdata;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.dns.util.DnsNameLabelUtil;

public class DnsTextRdataDecoder implements DnsRdataDecoder<String> {
    public static final DnsTextRdataDecoder DEFAULT = new DnsTextRdataDecoder();

    /**
     * Decode dns record data to text presentation.
     *
     * @param in record data
     * @param length record data length
     *
     * @return text presentation
     */
    @Override
    public String decodeRdata(ByteBuf in, @SuppressWarnings("unused") int length) {
        return DnsNameLabelUtil.decodeName(in);
    }
}
