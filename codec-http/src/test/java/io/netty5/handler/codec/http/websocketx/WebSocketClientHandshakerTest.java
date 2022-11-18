/*
 * Copyright 2015 The Netty Project
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
package io.netty5.handler.codec.http.websocketx;

import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferAllocator;
import io.netty5.buffer.CompositeBuffer;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.SimpleChannelInboundHandler;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.handler.codec.http.DefaultFullHttpResponse;
import io.netty5.handler.codec.http.DefaultHttpContent;
import io.netty5.handler.codec.http.FullHttpRequest;
import io.netty5.handler.codec.http.FullHttpResponse;
import io.netty5.handler.codec.http.HttpClientCodec;
import io.netty5.handler.codec.http.HttpHeaderNames;
import io.netty5.handler.codec.http.HttpObjectAggregator;
import io.netty5.handler.codec.http.HttpRequestEncoder;
import io.netty5.handler.codec.http.HttpResponseDecoder;
import io.netty5.handler.codec.http.HttpResponseStatus;
import io.netty5.handler.codec.http.HttpVersion;
import io.netty5.handler.codec.http.headers.HttpHeaders;
import io.netty5.util.concurrent.Future;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static io.netty5.buffer.DefaultBufferAllocators.preferredAllocator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class WebSocketClientHandshakerTest {
    protected abstract WebSocketClientHandshaker newHandshaker(URI uri, String subprotocol, HttpHeaders headers,
                                                               boolean absoluteUpgradeUrl,
                                                               boolean generateOriginHeader);

    protected WebSocketClientHandshaker newHandshaker(URI uri) {
        return newHandshaker(uri, null, null, false, true);
    }

    protected abstract CharSequence getOriginHeaderName();

    protected abstract CharSequence getProtocolHeaderName();

    protected abstract CharSequence[] getHandshakeRequiredHeaderNames();

    @Test
    void hostHeaderWs() {
        for (String scheme : new String[] { "ws://", "http://" }) {
            for (String host : new String[] { "localhost", "127.0.0.1", "[::1]", "Netty.io" }) {
                String enter = scheme + host;

                testHostHeader(enter, host);
                testHostHeader(enter + '/', host);
                testHostHeader(enter + ":80", host);
                testHostHeader(enter + ":443", host + ":443");
                testHostHeader(enter + ":9999", host + ":9999");
                testHostHeader(enter + "/path", host);
                testHostHeader(enter + ":80/path", host);
                testHostHeader(enter + ":443/path", host + ":443");
                testHostHeader(enter + ":9999/path", host + ":9999");
            }
        }
    }

    @Test
    void hostHeaderWss() {
        for (String scheme : new String[] { "wss://", "https://" }) {
            for (String host : new String[] { "localhost", "127.0.0.1", "[::1]", "Netty.io" }) {
                String enter = scheme + host;

                testHostHeader(enter, host);
                testHostHeader(enter + '/', host);
                testHostHeader(enter + ":80", host + ":80");
                testHostHeader(enter + ":443", host);
                testHostHeader(enter + ":9999", host + ":9999");
                testHostHeader(enter + "/path", host);
                testHostHeader(enter + ":80/path", host + ":80");
                testHostHeader(enter + ":443/path", host);
                testHostHeader(enter + ":9999/path", host + ":9999");
            }
        }
    }

    @Test
    void hostHeaderWithoutScheme() {
        testHostHeader("//localhost/", "localhost");
        testHostHeader("//localhost/path", "localhost");
        testHostHeader("//localhost:80/", "localhost:80");
        testHostHeader("//localhost:443/", "localhost:443");
        testHostHeader("//localhost:9999/", "localhost:9999");
    }

    @Test
    void testUpgradeUrl() {
        URI uri = URI.create("ws://localhost:9999/path%20with%20ws");
        WebSocketClientHandshaker handshaker = newHandshaker(uri);
        try (FullHttpRequest request = handshaker.newHandshakeRequest(preferredAllocator())) {
            assertEquals("/path%20with%20ws", request.uri());
        }
    }

    @Test
    void testUpgradeUrlWithQuery() {
        URI uri = URI.create("ws://localhost:9999/path%20with%20ws?a=b%20c");
        WebSocketClientHandshaker handshaker = newHandshaker(uri);
        try (FullHttpRequest request = handshaker.newHandshakeRequest(preferredAllocator())) {
            assertEquals("/path%20with%20ws?a=b%20c", request.uri());
        }
    }

    @Test
    void testUpgradeUrlWithoutPath() {
        URI uri = URI.create("ws://localhost:9999");
        WebSocketClientHandshaker handshaker = newHandshaker(uri);
        try (FullHttpRequest request = handshaker.newHandshakeRequest(preferredAllocator())) {
            assertEquals("/", request.uri());
        }
    }

    @Test
    void testUpgradeUrlWithoutPathWithQuery() {
        URI uri = URI.create("ws://localhost:9999?a=b%20c");
        WebSocketClientHandshaker handshaker = newHandshaker(uri);
        try (FullHttpRequest request = handshaker.newHandshakeRequest(preferredAllocator())) {
            assertEquals("/?a=b%20c", request.uri());
        }
    }

    @Test
    void testAbsoluteUpgradeUrlWithQuery() {
        URI uri = URI.create("ws://localhost:9999/path%20with%20ws?a=b%20c");
        WebSocketClientHandshaker handshaker = newHandshaker(uri, null, null, true, true);
        try (FullHttpRequest request = handshaker.newHandshakeRequest(preferredAllocator())) {
            assertEquals("ws://localhost:9999/path%20with%20ws?a=b%20c", request.uri());
        }
    }

    @Test
    void testHttpResponseAndFrameInSameBuffer() throws Exception {
        testHttpResponseAndFrameInSameBuffer(false);
    }

    @Test
    void testHttpResponseAndFrameInSameBufferCodec() throws Exception {
        testHttpResponseAndFrameInSameBuffer(true);
    }

    private void testHttpResponseAndFrameInSameBuffer(boolean codec) throws Exception {
        String url = "ws://localhost:9999/ws";
        final WebSocketClientHandshaker shaker = newHandshaker(URI.create(url));
        final WebSocketClientHandshaker handshaker = new WebSocketClientHandshaker(
                shaker.uri(), shaker.version(), null, HttpHeaders.emptyHeaders(), Integer.MAX_VALUE, -1) {
            @Override
            protected FullHttpRequest newHandshakeRequest(BufferAllocator allocator) {
                return shaker.newHandshakeRequest(allocator);
            }

            @Override
            protected void verify(FullHttpResponse response) {
                // Not do any verification, so we not need to care sending the correct headers etc in the test,
                // which would just make things more complicated.
            }

            @Override
            protected WebSocketFrameDecoder newWebsocketDecoder() {
                return shaker.newWebsocketDecoder();
            }

            @Override
            protected WebSocketFrameEncoder newWebSocketEncoder() {
                return shaker.newWebSocketEncoder();
            }
        };

        // use randomBytes helper from utils to check that it functions properly
        byte[] data = WebSocketUtil.randomBytes(24);

        // Create a EmbeddedChannel which we will use to encode a BinaryWebsocketFrame to bytes and so use these
        // to test the actual handshaker.
        WebSocketServerHandshakerFactory factory = new WebSocketServerHandshakerFactory(url, null, false);
        FullHttpRequest request = shaker.newHandshakeRequest(preferredAllocator());
        WebSocketServerHandshaker socketServerHandshaker = factory.newHandshaker(request);
        request.close();
        EmbeddedChannel websocketChannel = new EmbeddedChannel(socketServerHandshaker.newWebSocketEncoder(),
                                                               socketServerHandshaker.newWebsocketDecoder());
        assertTrue(websocketChannel.writeOutbound(
                new BinaryWebSocketFrame(websocketChannel.bufferAllocator().copyOf(data))));

        byte[] bytes = "HTTP/1.1 101 Switching Protocols\r\n\r\n".getBytes(StandardCharsets.US_ASCII);

        CompositeBuffer compositeBuffer = websocketChannel.bufferAllocator().compose();
        compositeBuffer.extendWith(websocketChannel.bufferAllocator().allocate(bytes.length).writeBytes(bytes).send());
        for (;;) {
            final Buffer buffer = websocketChannel.readOutbound();
            if (buffer == null) {
                break;
            }
            compositeBuffer.extendWith(buffer.send());
        }

        EmbeddedChannel ch = new EmbeddedChannel(new HttpObjectAggregator<DefaultHttpContent>(Integer.MAX_VALUE),
                                                 new SimpleChannelInboundHandler<FullHttpResponse>() {
                                                     @Override
                                                     protected void messageReceived(ChannelHandlerContext ctx,
                                                                                    FullHttpResponse msg) {
                                                         handshaker.finishHandshake(ctx.channel(), msg);
                                                         ctx.pipeline().remove(this);
                                                     }
                                                 });
        if (codec) {
            ch.pipeline().addFirst(new HttpClientCodec());
        } else {
            ch.pipeline().addFirst(new HttpRequestEncoder(), new HttpResponseDecoder());
        }
        // We need to first write the request as HttpClientCodec will fail if we receive a response before a request
        // was written.
        shaker.handshake(ch).asStage().sync();
        for (;;) {
            // Just consume the bytes, we are not interested in these.
            try (Buffer buf = ch.readOutbound()) {
                if (buf == null) {
                    break;
                }
            }
        }
        assertTrue(ch.writeInbound(compositeBuffer));
        assertTrue(ch.finish());

        try (BinaryWebSocketFrame frame = ch.readInbound();
             Buffer expect = ch.bufferAllocator().allocate(data.length).writeBytes(data)) {
            assertEquals(expect, frame.binaryData());
            assertTrue(frame.isFinalFragment());
            assertEquals(0, frame.rsv());
        }
    }

    @Test
    void testDuplicateWebsocketHandshakeHeaders() {
        URI uri = URI.create("ws://localhost:9999/foo");

        HttpHeaders inputHeaders = HttpHeaders.newHeaders();
        String bogusSubProtocol = "bogusSubProtocol";
        String bogusHeaderValue = "bogusHeaderValue";

        // add values for the headers that are reserved for use in the websockets handshake
        for (CharSequence header : getHandshakeRequiredHeaderNames()) {
            if (!HttpHeaderNames.HOST.equals(header)) {
                inputHeaders.add(header, bogusHeaderValue);
            }
        }
        inputHeaders.add(getProtocolHeaderName(), bogusSubProtocol);

        String realSubProtocol = "realSubProtocol";
        WebSocketClientHandshaker handshaker = newHandshaker(uri, realSubProtocol, inputHeaders, false, true);
        FullHttpRequest request = handshaker.newHandshakeRequest(preferredAllocator());
        HttpHeaders outputHeaders = request.headers();

        // the header values passed in originally have been replaced with values generated by the Handshaker
        for (CharSequence header : getHandshakeRequiredHeaderNames()) {
            assertThat(outputHeaders.values(header)).hasSize(1);
            assertNotEquals(bogusHeaderValue, outputHeaders.get(header));
        }

        // the subprotocol header value is that of the subprotocol string passed into the Handshaker
        assertThat(outputHeaders.values(getProtocolHeaderName())).hasSize(1);
        assertEquals(realSubProtocol, outputHeaders.get(getProtocolHeaderName()));

        request.close();
    }

    @Test
    void testSetHostHeaderIfNoPresentInCustomHeaders() {
        var customHeaders = HttpHeaders.newHeaders();
        customHeaders.set(HttpHeaderNames.HOST, "custom-host");
        var handshaker = newHandshaker(URI.create("ws://localhost:9999/ws"), null,
                                       customHeaders, false, true);
        try (var request = handshaker.newHandshakeRequest(preferredAllocator())) {
            assertEquals("custom-host", request.headers().get(HttpHeaderNames.HOST));
        }
    }

    @Test
    void testNoOriginHeaderInHandshakeRequest() {
        var handshaker = newHandshaker(URI.create("ws://localhost:9999/ws"), null,
                                       null, false, false);
        try (var request = handshaker.newHandshakeRequest(preferredAllocator())) {
            assertNull(request.headers().get(HttpHeaderNames.ORIGIN));
        }
    }

    @Test
    void testSetOriginFromCustomHeaders() {
        var customHeaders = HttpHeaders.newHeaders().set(HttpHeaderNames.ORIGIN, "http://example.com");
        var handshaker = newHandshaker(URI.create("ws://server.example.com/chat"), null,
                                                             customHeaders, false, true);
        try (var request = handshaker.newHandshakeRequest(preferredAllocator())) {
            assertEquals("http://example.com", request.headers().get(HttpHeaderNames.ORIGIN));
        }
    }

    @Test
    void testWebSocketClientHandshakeExceptionContainsResponse() {
        var handshaker = newHandshaker(URI.create("ws://localhost:9999/ws"), null,
                                       null, false, true);
        var response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.UNAUTHORIZED, preferredAllocator().allocate(0));
        response.headers().set(HttpHeaderNames.WWW_AUTHENTICATE, "realm = access token required");

        final WebSocketClientHandshakeException exception;
        try (response) {
            exception = Assertions.assertThrows(WebSocketClientHandshakeException.class,
                                                () -> handshaker.finishHandshake(null, response));
        }
        assertEquals("Invalid handshake response status: 401 Unauthorized", exception.getMessage());
        assertNotNull(exception.response());
        assertEquals(HttpResponseStatus.UNAUTHORIZED, exception.response().status());
        assertEquals(1, exception.response().headers().size());
        assertTrue(exception.response().headers().contains(HttpHeaderNames.WWW_AUTHENTICATE,
                                                           "realm = access token required"));
    }

    @Test
    public void testOriginHeaderIsAbsentWhenGeneratingDisable() {
        URI uri = URI.create("http://example.com/ws");
        WebSocketClientHandshaker handshaker = newHandshaker(uri, null, null, false, false);

        try (var request = handshaker.newHandshakeRequest(preferredAllocator())) {
            assertFalse(request.headers().contains(getOriginHeaderName()));
            assertEquals("/ws", request.uri());
        }
    }

    @Test
    public void testInvalidHostWhenIncorrectWebSocketURI() {
        URI uri = URI.create("/ws");
        EmbeddedChannel channel = new EmbeddedChannel(new HttpClientCodec());
        final WebSocketClientHandshaker handshaker = newHandshaker(uri, null, null, false, true);
        final Future<Void> handshakeFuture = handshaker.handshake(channel);

        assertFalse(handshakeFuture.isSuccess());
        assertInstanceOf(IllegalArgumentException.class, handshakeFuture.cause());
        assertEquals("Cannot generate the 'host' header value, webSocketURI should contain host" +
                " or passed through customHeaders", handshakeFuture.cause().getMessage());
        assertFalse(channel.finish());
    }

    @Test
    public void testInvalidOriginWhenIncorrectWebSocketURI() {
        URI uri = URI.create("/ws");
        EmbeddedChannel channel = new EmbeddedChannel(new HttpClientCodec());
        HttpHeaders headers = HttpHeaders.newHeaders();
        headers.set(HttpHeaderNames.HOST, "localhost:80");
        final WebSocketClientHandshaker handshaker = newHandshaker(uri, null, headers, false, true);
        final Future<Void> handshakeFuture = handshaker.handshake(channel);

        assertFalse(handshakeFuture.isSuccess());
        assertInstanceOf(IllegalArgumentException.class, handshakeFuture.cause());
        assertEquals("Cannot generate the '" + getOriginHeaderName() + "' header value," +
                " webSocketURI should contain host or disable generateOriginHeader" +
                " or pass value through customHeaders", handshakeFuture.cause().getMessage());
        assertFalse(channel.finish());
    }

    private void testHostHeader(String uri, String expected) {
        var handshaker = newHandshaker(URI.create(uri));
        try (var request = handshaker.newHandshakeRequest(preferredAllocator())) {
            assertEquals(expected, request.headers().get(HttpHeaderNames.HOST));
        }
    }
}
