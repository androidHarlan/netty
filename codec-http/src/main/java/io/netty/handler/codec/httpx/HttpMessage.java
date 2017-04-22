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
package io.netty.handler.codec.httpx;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.stream.Stream;
import io.netty.handler.codec.http.HttpVersion;

/**
 */
public interface HttpMessage {
    HttpMessageType getType();

    HttpVersion getVersion();
    HttpMessage setVersion(HttpVersion version);

    HttpHeaders getHeaders();

    boolean hasTrailingHeaders();
    HttpHeaders getTrailingHeaders();

    boolean isContentStream();
    Object getContent();
    HttpMessage setContent(ByteBuf content);
    HttpMessage setContent(Stream<ByteBuf> content);
}
