/*
 * Copyright 2013 The Netty Project
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
package io.netty5.handler.codec.http;

import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferClosedException;
import io.netty5.handler.codec.http.headers.DefaultHttpHeadersFactory;
import io.netty5.handler.codec.http.headers.HttpHeaders;
import io.netty5.handler.codec.http.headers.HttpHeadersFactory;
import io.netty5.util.Send;

import static java.util.Objects.requireNonNull;

/**
 * Default implementation of a {@link FullHttpResponse}.
 */
public class DefaultFullHttpResponse extends DefaultHttpResponse implements FullHttpResponse {

    private final Buffer payload;
    private final HttpHeaders trailingHeaders;

    /**
     * Used to cache the value of the hash code and avoid {@link BufferClosedException}.
     */
    private int hash;

    /**
     * Create an HTTP response with the given HTTP version, status, and contents.
     */
    public DefaultFullHttpResponse(HttpVersion version, HttpResponseStatus status, Buffer payload) {
        this(version, status, payload, DefaultHttpHeadersFactory.headersFactory(),
                DefaultHttpHeadersFactory.trailersFactory());
    }

    /**
     * Create an HTTP response with the given HTTP version, status, contents,
     * and with headers and trailers created by the given header factories.
     * <p>
     * The recommended header factory is {@link DefaultHttpHeadersFactory#headersFactory()},
     * and the recommended trailer factory is {@link DefaultHttpHeadersFactory#trailersFactory()}.
     */
    public DefaultFullHttpResponse(HttpVersion version, HttpResponseStatus status, Buffer payload,
                                   HttpHeadersFactory headersFactory, HttpHeadersFactory trailersFactory) {
        super(version, status, headersFactory);
        this.payload = requireNonNull(payload, "payload");
        trailingHeaders = trailersFactory.newHeaders();
    }

    /**
     * Create an HTTP response with the given HTTP version, status, contents, headers and trailers.
     */
    public DefaultFullHttpResponse(HttpVersion version, HttpResponseStatus status,
                                   Buffer payload, HttpHeaders headers, HttpHeaders trailingHeaders) {
        super(version, status, headers);
        this.payload = requireNonNull(payload, "payload");
        this.trailingHeaders = requireNonNull(trailingHeaders, "trailingHeaders");
    }

    @Override
    public void close() {
        payload.close();
    }

    @Override
    public boolean isAccessible() {
        return payload.isAccessible();
    }

    @Override
    public FullHttpResponse touch(Object hint) {
        payload.touch(hint);
        return this;
    }

    @Override
    public Buffer payload() {
        return payload;
    }

    @Override
    public Send<FullHttpResponse> send() {
        return payload.send().map(FullHttpResponse.class,
                payload -> new DefaultFullHttpResponse(protocolVersion(), status(), payload, headers(),
                        trailingHeaders));
    }

    @Override
    public DefaultFullHttpResponse copy() {
        return new DefaultFullHttpResponse(
                protocolVersion(), status(), payload.copy(), headers().copy(), trailingHeaders.copy());
    }

    @Override
    public HttpHeaders trailingHeaders() {
        return trailingHeaders;
    }

    @Override
    public FullHttpResponse setProtocolVersion(HttpVersion version) {
        super.setProtocolVersion(version);
        return this;
    }

    @Override
    public FullHttpResponse setStatus(HttpResponseStatus status) {
        super.setStatus(status);
        return this;
    }

    @Override
    public int hashCode() {
        int hash = this.hash;
        if (hash == 0) {
            final Buffer payload = payload();
            if (payload.isAccessible()) {
                try {
                    hash = 31 + payload.hashCode();
                } catch (BufferClosedException ignored) {
                    // Handle race condition between liveness checking and using the object.
                    hash = 31;
                }
            } else {
                hash = 31;
            }
            hash = 31 * hash + trailingHeaders().hashCode();
            hash = 31 * hash + super.hashCode();
            this.hash = hash;
        }
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DefaultFullHttpResponse)) {
            return false;
        }

        DefaultFullHttpResponse other = (DefaultFullHttpResponse) o;

        return super.equals(other) &&
               payload().equals(other.payload()) &&
               trailingHeaders().equals(other.trailingHeaders());
    }

    @Override
    public String toString() {
        return HttpMessageUtil.appendFullResponse(new StringBuilder(256), this).toString();
    }
}
