/*
 * Copyright 2018 The Netty Project
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
package io.netty.handler.codec.http.cache;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.ReadOnlyHttpHeaders;

import java.util.Date;

import static io.netty.buffer.Unpooled.*;
import static io.netty.handler.codec.http.cache.CacheControlDecoder.*;

class HttpResponseFromCacheGenerator {

    FullHttpResponse generate(final HttpRequest request, final HttpCacheEntry cacheEntry) {
        final Date now = new Date();

        final DefaultHttpHeaders headers = new DefaultHttpHeaders(false);
        headers.set(cacheEntry.getResponseHeaders());

        ByteBuf content = EMPTY_BUFFER;
        final ByteBufHolder contentHolder = cacheEntry.getContent();
        if (request.method().equals(HttpMethod.GET) && content != null) {
            content = contentHolder.content();

            if (headers.get(HttpHeaderNames.TRANSFER_ENCODING) == null &&
                headers.get(HttpHeaderNames.CONTENT_LENGTH) == null) {
                headers.setInt(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
            }
        }

        final long age = cacheEntry.getCurrentAgeInSeconds(now);
        if (age > 0) {
            if (age >= MAXIMUM_AGE) {
                headers.add(HttpHeaderNames.AGE, Integer.toString(MAXIMUM_AGE));
            } else {
                headers.add(HttpHeaderNames.AGE, Long.toString(age));
            }
        }

        return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, cacheEntry.getStatus(), content, headers,
                                           new ReadOnlyHttpHeaders(false));
    }
}
