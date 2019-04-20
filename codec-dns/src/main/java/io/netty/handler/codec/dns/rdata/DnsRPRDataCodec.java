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
import io.netty.handler.codec.dns.record.DnsRPRecord;

import static io.netty.handler.codec.dns.util.DnsDecodeUtil.*;
import static io.netty.handler.codec.dns.util.DnsEncodeUtil.*;

/**
 * Codec for {@link DnsRPRecord}
 */
public class DnsRPRDataCodec implements DnsRDataCodec<DnsRPRecord> {
    public static final DnsRPRDataCodec DEFAULT = new DnsRPRDataCodec();

    @Override
    public DnsRPRecord decodeRData(String name, int dnsClass, long timeToLive, ByteBuf rData) {
        String mbox = decodeDomainName(rData);
        String txt = decodeDomainName(rData);
        return new DnsRPRecord(name, dnsClass, timeToLive, mbox, txt);
    }

    @Override
    public void encodeRData(DnsRPRecord record, ByteBuf out) {
        encodeDomainName(record.mbox(), out);
        encodeDomainName(record.txt(), out);
    }
}

