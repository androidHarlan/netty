/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT;
import static io.netty.handler.codec.http2.Http2CodecUtil.emptyPingBuf;
import static io.netty.handler.codec.http2.Http2Error.NO_ERROR;
import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2Headers.EMPTY_HEADERS;
import static io.netty.handler.codec.http2.Http2Stream.State.HALF_CLOSED_LOCAL;
import static io.netty.handler.codec.http2.Http2Stream.State.OPEN;
import static io.netty.handler.codec.http2.Http2Stream.State.RESERVED_LOCAL;
import static io.netty.handler.codec.http2.Http2Stream.State.RESERVED_REMOTE;
import static io.netty.util.CharsetUtil.UTF_8;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyShort;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link DelegatingHttp2ConnectionHandlerTest} and its base class
 * {@link AbstractHttp2ConnectionHandler}.
 */
public class DelegatingHttp2ConnectionHandlerTest {
    private static final int STREAM_ID = 1;
    private static final int PUSH_STREAM_ID = 2;

    private DelegatingHttp2ConnectionHandler handler;

    @Mock
    private Http2Connection connection;

    @Mock
    private Http2Connection.Endpoint remote;

    @Mock
    private Http2Connection.Endpoint local;

    @Mock
    private Http2InboundFlowController inboundFlow;

    @Mock
    private Http2OutboundFlowController outboundFlow;

    @Mock
    private ChannelHandlerContext ctx;

    @Mock
    private Channel channel;

    @Mock
    private ChannelPromise promise;

    @Mock
    private ChannelFuture future;

    @Mock
    private Http2Stream stream;

    @Mock
    private Http2Stream pushStream;

    @Mock
    private Http2FrameObserver observer;

    @Mock
    private Http2FrameReader reader;

    @Mock
    private Http2FrameWriter writer;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(channel.isActive()).thenReturn(true);
        when(stream.id()).thenReturn(STREAM_ID);
        when(stream.state()).thenReturn(OPEN);
        when(pushStream.id()).thenReturn(PUSH_STREAM_ID);
        when(connection.activeStreams()).thenReturn(Arrays.asList(stream));
        when(connection.stream(STREAM_ID)).thenReturn(stream);
        when(connection.requireStream(STREAM_ID)).thenReturn(stream);
        when(connection.local()).thenReturn(local);
        when(connection.remote()).thenReturn(remote);
        when(local.createStream(eq(STREAM_ID), anyBoolean())).thenReturn(stream);
        when(local.reservePushStream(eq(PUSH_STREAM_ID), eq(stream))).thenReturn(pushStream);
        when(remote.createStream(eq(STREAM_ID), anyBoolean())).thenReturn(stream);
        when(remote.reservePushStream(eq(PUSH_STREAM_ID), eq(stream))).thenReturn(pushStream);
        when(writer.writeSettings(eq(ctx), eq(promise), any(Http2Settings.class))).thenReturn(
                future);
        when(writer.writeGoAway(eq(ctx), eq(promise), anyInt(), anyInt(), any(ByteBuf.class)))
                .thenReturn(future);
        mockContext();

        handler =
                new DelegatingHttp2ConnectionHandler(connection, reader, writer, inboundFlow,
                        outboundFlow, observer);

        // Simulate activation of the handler to force writing the initial settings.
        Http2Settings settings = new Http2Settings();
        settings.allowCompressedData(true);
        settings.initialWindowSize(10);
        settings.pushEnabled(true);
        settings.maxConcurrentStreams(100);
        settings.maxHeaderTableSize(200);
        when(local.allowCompressedData()).thenReturn(true);
        when(inboundFlow.initialInboundWindowSize()).thenReturn(10);
        when(local.allowPushTo()).thenReturn(true);
        when(remote.maxStreams()).thenReturn(100);
        when(reader.maxHeaderTableSize()).thenReturn(200);
        handler.handlerAdded(ctx);
        verify(writer).writeSettings(eq(ctx), eq(promise), eq(settings));

        // Simulate receiving the initial settings from the remote endpoint.
        decode().onSettingsRead(new Http2Settings());
        verify(observer).onSettingsRead(new Http2Settings());
        verify(writer).writeSettingsAck(eq(ctx), eq(promise));

        // Re-mock the context so no calls are registered.
        mockContext();
        handler.handlerAdded(ctx);
    }

    @Test
    public void closeShouldSendGoAway() throws Exception {
        handler.close(ctx, promise);
        verify(writer).writeGoAway(eq(ctx), eq(promise), eq(0), eq((long) NO_ERROR.code()),
                eq(EMPTY_BUFFER));
        verify(connection).goAwaySent();
    }

    @Test
    public void channelInactiveShouldCloseStreams() throws Exception {
        handler.channelInactive(ctx);
        verify(stream).close();
        verify(inboundFlow).removeStream(STREAM_ID);
        verify(outboundFlow).removeStream(STREAM_ID);
    }

    @Test
    public void streamErrorShouldCloseStream() throws Exception {
        Http2Exception e = new Http2StreamException(STREAM_ID, PROTOCOL_ERROR);
        handler.exceptionCaught(ctx, e);
        verify(stream).close();
        verify(inboundFlow).removeStream(STREAM_ID);
        verify(outboundFlow).removeStream(STREAM_ID);
        verify(writer).writeRstStream(eq(ctx), eq(promise), eq(STREAM_ID),
                eq((long) PROTOCOL_ERROR.code()));
    }

    @Test
    public void connectionErrorShouldSendGoAway() throws Exception {
        Http2Exception e = new Http2Exception(PROTOCOL_ERROR);
        handler.exceptionCaught(ctx, e);
        verify(connection).goAwaySent();
        verify(writer).writeGoAway(eq(ctx), eq(promise), eq(0), eq((long) PROTOCOL_ERROR.code()),
                eq(EMPTY_BUFFER));
    }

    @Test
    public void dataReadAfterGoAwayShouldApplyFlowControl() throws Exception {
        when(connection.isGoAwaySent()).thenReturn(true);
        decode().onDataRead(STREAM_ID, dummyData(), 10, true, true, true);
        verify(inboundFlow).applyInboundFlowControl(eq(STREAM_ID), eq(dummyData()), eq(10),
                eq(true), eq(true), eq(true), any(Http2InboundFlowController.FrameWriter.class));

        // Verify that the event was absorbed and not propagated to the oberver.
        verify(observer, never()).onDataRead(anyInt(), any(ByteBuf.class), anyInt(), anyBoolean(),
                anyBoolean(), anyBoolean());
    }

    @Test
    public void dataReadWithEndOfStreamShouldCloseRemoteSide() throws Exception {
        decode().onDataRead(STREAM_ID, dummyData(), 10, true, false, false);
        verify(inboundFlow).applyInboundFlowControl(eq(STREAM_ID), eq(dummyData()), eq(10),
                eq(true), eq(false), eq(false), any(Http2InboundFlowController.FrameWriter.class));
        verify(stream).closeRemoteSide();
        verify(observer).onDataRead(eq(STREAM_ID), eq(dummyData()), eq(10), eq(true), eq(false),
                eq(false));
    }

    @Test
    public void dataReadWithShouldAllowCompression() throws Exception {
        when(local.allowCompressedData()).thenReturn(true);
        decode().onDataRead(STREAM_ID, dummyData(), 10, false, false, true);
        verify(inboundFlow).applyInboundFlowControl(eq(STREAM_ID), eq(dummyData()), eq(10),
                eq(false), eq(false), eq(true), any(Http2InboundFlowController.FrameWriter.class));
        verify(stream, never()).closeRemoteSide();
        verify(observer).onDataRead(eq(STREAM_ID), eq(dummyData()), eq(10), eq(false), eq(false),
                eq(true));
    }

    @Test(expected = Http2Exception.class)
    public void dataReadShouldDisallowCompression() throws Exception {
        when(local.allowCompressedData()).thenReturn(false);
        decode().onDataRead(STREAM_ID, dummyData(), 10, false, false, true);
    }

    @Test
    public void headersReadAfterGoAwayShouldBeIgnored() throws Exception {
        when(connection.isGoAwaySent()).thenReturn(true);
        decode().onHeadersRead(STREAM_ID, EMPTY_HEADERS, 0, false, false);
        verify(remote, never()).createStream(eq(STREAM_ID), eq(false));

        // Verify that the event was absorbed and not propagated to the oberver.
        verify(observer, never()).onHeadersRead(anyInt(), any(Http2Headers.class), anyInt(),
                anyBoolean(), anyBoolean());
        verify(outboundFlow, never()).updateStream(anyInt(), anyInt(), anyShort(), anyBoolean());
        verify(inboundFlow, never()).addStream(anyInt());
    }

    @Test
    public void headersReadForUnknownStreamShouldCreateStream() throws Exception {
        decode().onHeadersRead(5, EMPTY_HEADERS, 0, false, false);
        verify(remote).createStream(eq(5), eq(false));
        verify(outboundFlow).addStream(eq(5), eq(0), eq(DEFAULT_PRIORITY_WEIGHT), eq(false));
        verify(inboundFlow).addStream(5);
        verify(observer).onHeadersRead(eq(5), eq(EMPTY_HEADERS), eq(0),
                eq(DEFAULT_PRIORITY_WEIGHT), eq(false), eq(0), eq(false), eq(false));
    }

    @Test
    public void headersReadForUnknownStreamShouldCreateHalfClosedStream() throws Exception {
        decode().onHeadersRead(5, EMPTY_HEADERS, 0, true, false);
        verify(remote).createStream(eq(5), eq(true));
        verify(outboundFlow).addStream(eq(5), eq(0), eq(DEFAULT_PRIORITY_WEIGHT), eq(false));
        verify(inboundFlow, never()).addStream(5);
        verify(observer).onHeadersRead(eq(5), eq(EMPTY_HEADERS), eq(0),
                eq(DEFAULT_PRIORITY_WEIGHT), eq(false), eq(0), eq(true), eq(false));
    }

    @Test
    public void headersReadForPromisedStreamShouldHalfOpenStream() throws Exception {
        when(stream.state()).thenReturn(RESERVED_REMOTE);
        decode().onHeadersRead(STREAM_ID, EMPTY_HEADERS, 0, false, false);
        verify(stream).openForPush();
        verify(outboundFlow, never()).addStream(anyInt(), anyInt(), anyShort(), anyBoolean());
        verify(inboundFlow).addStream(STREAM_ID);
        verify(observer).onHeadersRead(eq(STREAM_ID), eq(EMPTY_HEADERS), eq(0),
                eq(DEFAULT_PRIORITY_WEIGHT), eq(false), eq(0), eq(false), eq(false));
    }

    @Test
    public void headersReadForPromisedStreamShouldCloseStream() throws Exception {
        when(stream.state()).thenReturn(RESERVED_REMOTE);
        decode().onHeadersRead(STREAM_ID, EMPTY_HEADERS, 0, true, false);
        verify(stream).openForPush();
        verify(stream).close();
        verify(outboundFlow, never()).addStream(anyInt(), anyInt(), anyShort(), anyBoolean());
        verify(inboundFlow, never()).addStream(STREAM_ID);
        verify(outboundFlow).removeStream(STREAM_ID);
        verify(inboundFlow).removeStream(STREAM_ID);
        verify(observer).onHeadersRead(eq(STREAM_ID), eq(EMPTY_HEADERS), eq(0),
                eq(DEFAULT_PRIORITY_WEIGHT), eq(false), eq(0), eq(true), eq(false));
    }

    @Test
    public void pushPromiseReadAfterGoAwayShouldBeIgnored() throws Exception {
        when(connection.isGoAwaySent()).thenReturn(true);
        decode().onPushPromiseRead(STREAM_ID, PUSH_STREAM_ID, EMPTY_HEADERS, 0);
        verify(remote, never()).reservePushStream(anyInt(), any(Http2Stream.class));
        verify(observer, never()).onPushPromiseRead(anyInt(), anyInt(), any(Http2Headers.class),
                anyInt());
    }

    @Test
    public void pushPromiseReadShouldSucceed() throws Exception {
        decode().onPushPromiseRead(STREAM_ID, PUSH_STREAM_ID, EMPTY_HEADERS, 0);
        verify(remote).reservePushStream(eq(PUSH_STREAM_ID), eq(stream));
        verify(observer).onPushPromiseRead(eq(STREAM_ID), eq(PUSH_STREAM_ID), eq(EMPTY_HEADERS),
                eq(0));
    }

    @Test
    public void priorityReadAfterGoAwayShouldBeIgnored() throws Exception {
        when(connection.isGoAwaySent()).thenReturn(true);
        decode().onPriorityRead(STREAM_ID, 0, (short) 255, true);
        verify(outboundFlow, never()).updateStream(anyInt(), anyInt(), anyShort(), anyBoolean());
        verify(observer, never()).onPriorityRead(anyInt(), anyInt(), anyShort(), anyBoolean());
    }

    @Test
    public void priorityReadForUnknownStreamShouldUpdateFlowController() throws Exception {
        // The outbound flow controller may keep a prioritized stream around for some time after
        // being closed. Verify that the flow controller is updated regardless of the presence of
        // the stream.
        decode().onPriorityRead(5, 0, (short) 255, true);
        verify(outboundFlow).updateStream(eq(5), eq(0), eq((short) 255), eq(true));
        verify(observer).onPriorityRead(eq(5), eq(0), eq((short) 255), eq(true));
    }

    @Test
    public void priorityReadShouldSucceed() throws Exception {
        decode().onPriorityRead(STREAM_ID, 0, (short) 255, true);
        verify(outboundFlow).updateStream(eq(STREAM_ID), eq(0), eq((short) 255), eq(true));
        verify(observer).onPriorityRead(eq(STREAM_ID), eq(0), eq((short) 255), eq(true));
    }

    @Test
    public void windowUpdateReadAfterGoAwayShouldBeIgnored() throws Exception {
        when(connection.isGoAwaySent()).thenReturn(true);
        decode().onWindowUpdateRead(STREAM_ID, 10);
        verify(outboundFlow, never()).updateOutboundWindowSize(anyInt(), anyInt());
        verify(observer, never()).onWindowUpdateRead(anyInt(), anyInt());
    }

    @Test
    public void windowUpdateReadForUnknownStreamShouldBeIgnored() throws Exception {
        decode().onWindowUpdateRead(5, 10);
        verify(outboundFlow, never()).updateOutboundWindowSize(anyInt(), anyInt());
        verify(observer, never()).onWindowUpdateRead(anyInt(), anyInt());
    }

    @Test
    public void windowUpdateReadShouldSucceed() throws Exception {
        decode().onWindowUpdateRead(STREAM_ID, 10);
        verify(outboundFlow).updateOutboundWindowSize(eq(STREAM_ID), eq(10));
        verify(observer).onWindowUpdateRead(eq(STREAM_ID), eq(10));
    }

    @Test
    public void rstStreamReadAfterGoAwayShouldBeIgnored() throws Exception {
        when(connection.isGoAwaySent()).thenReturn(true);
        decode().onRstStreamRead(STREAM_ID, PROTOCOL_ERROR.code());
        verify(stream, never()).close();
        verify(observer, never()).onRstStreamRead(anyInt(), anyLong());
    }

    @Test
    public void rstStreamReadForUnknownStreamShouldBeIgnored() throws Exception {
        decode().onRstStreamRead(5, PROTOCOL_ERROR.code());
        verify(stream, never()).close();
        verify(observer, never()).onRstStreamRead(anyInt(), anyLong());
    }

    @Test
    public void rstStreamReadShouldCloseStream() throws Exception {
        decode().onRstStreamRead(STREAM_ID, PROTOCOL_ERROR.code());
        verify(stream).close();
        verify(observer).onRstStreamRead(eq(STREAM_ID), eq((long) PROTOCOL_ERROR.code()));
    }

    @Test
    public void pingReadWithAckShouldNotifyObserver() throws Exception {
        decode().onPingAckRead(emptyPingBuf());
        verify(observer).onPingAckRead(eq(emptyPingBuf()));
    }

    @Test
    public void pingReadShouldReplyWithAck() throws Exception {
        decode().onPingRead(emptyPingBuf());
        verify(writer).writePing(eq(ctx), eq(promise), eq(true), eq(emptyPingBuf()));
        verify(observer, never()).onPingAckRead(any(ByteBuf.class));
    }

    @Test
    public void settingsReadWithAckShouldNotifyObserver() throws Exception {
        decode().onSettingsAckRead();
        verify(observer).onSettingsAckRead();
    }

    @Test
    public void settingsReadShouldSetValues() throws Exception {
        Http2Settings settings = new Http2Settings();
        settings.pushEnabled(true);
        settings.initialWindowSize(123);
        settings.maxConcurrentStreams(456);
        settings.allowCompressedData(true);
        settings.maxHeaderTableSize(789);
        decode().onSettingsRead(settings);
        verify(remote).allowPushTo(true);
        verify(outboundFlow).initialOutboundWindowSize(123);
        verify(local).maxStreams(456);
        assertTrue(handler.settings().allowCompressedData());
        verify(writer).maxHeaderTableSize(789);
        // Take into account the time this was called during setup().
        verify(writer, times(2)).writeSettingsAck(eq(ctx), eq(promise));
        verify(observer).onSettingsRead(eq(settings));
    }

    @Test
    public void goAwayShoultShouldUpdateConnectionState() throws Exception {
        decode().onGoAwayRead(1, 2, EMPTY_BUFFER);
        verify(connection).goAwayReceived();
        verify(observer).onGoAwayRead(eq(1), eq(2L), eq(EMPTY_BUFFER));
    }

    @Test(expected = Http2Exception.class)
    public void dataWriteAfterGoAwayShouldFail() throws Exception {
        when(connection.isGoAway()).thenReturn(true);
        handler.writeData(ctx, promise, STREAM_ID, dummyData(), 0, false, false, false);
    }

    @Test(expected = Http2Exception.class)
    public void dataWriteShouldDisallowCompression() throws Exception {
        when(local.allowCompressedData()).thenReturn(false);
        handler.writeData(ctx, promise, STREAM_ID, dummyData(), 0, false, false, true);
    }

    @Test
    public void dataWriteShouldAllowCompression() throws Exception {
        when(remote.allowCompressedData()).thenReturn(true);
        handler.writeData(ctx, promise, STREAM_ID, dummyData(), 0, false, false, true);
        verify(outboundFlow).sendFlowControlled(eq(STREAM_ID), eq(dummyData()), eq(0), eq(false),
                eq(false), eq(true), any(Http2OutboundFlowController.FrameWriter.class));
    }

    @Test
    public void dataWriteShouldSucceed() throws Exception {
        handler.writeData(ctx, promise, STREAM_ID, dummyData(), 0, false, false, false);
        verify(outboundFlow).sendFlowControlled(eq(STREAM_ID), eq(dummyData()), eq(0), eq(false),
                eq(false), eq(false), any(Http2OutboundFlowController.FrameWriter.class));
    }

    @Test(expected = Http2Exception.class)
    public void headersWriteAfterGoAwayShouldFail() throws Exception {
        when(connection.isGoAway()).thenReturn(true);
        handler.writeHeaders(ctx, promise, 5, EMPTY_HEADERS, 0, (short) 255, false, 0, false, false);
        verify(writer, never()).writeHeaders(eq(ctx), eq(promise), anyInt(),
                any(Http2Headers.class), anyInt(), anyBoolean(), anyBoolean());
        verify(outboundFlow, never()).addStream(anyInt(), anyInt(), anyShort(), anyBoolean());
        verify(inboundFlow, never()).addStream(anyInt());
    }

    @Test
    public void headersWriteForUnknownStreamShouldCreateStream() throws Exception {
        handler.writeHeaders(ctx, promise, 5, EMPTY_HEADERS, 0, false, false);
        verify(local).createStream(eq(5), eq(false));
        verify(writer).writeHeaders(eq(ctx), eq(promise), eq(5), eq(EMPTY_HEADERS), eq(0),
                eq(DEFAULT_PRIORITY_WEIGHT), eq(false), eq(0), eq(false), eq(false));
        verify(outboundFlow).addStream(eq(5), eq(0), eq(DEFAULT_PRIORITY_WEIGHT), eq(false));
        verify(inboundFlow).addStream(5);
    }

    @Test
    public void headersWriteShouldCreateHalfClosedStream() throws Exception {
        handler.writeHeaders(ctx, promise, 5, EMPTY_HEADERS, 0, true, false);
        verify(local).createStream(eq(5), eq(true));
        verify(writer).writeHeaders(eq(ctx), eq(promise), eq(5), eq(EMPTY_HEADERS), eq(0),
                eq(DEFAULT_PRIORITY_WEIGHT), eq(false), eq(0), eq(true), eq(false));
        verify(outboundFlow, never()).addStream(anyInt(), anyInt(), anyShort(), anyBoolean());
        verify(inboundFlow).addStream(5);
    }

    @Test
    public void headersWriteShouldOpenStreamForPush() throws Exception {
        when(stream.state()).thenReturn(RESERVED_LOCAL);
        handler.writeHeaders(ctx, promise, STREAM_ID, EMPTY_HEADERS, 0, false, false);
        verify(stream).openForPush();
        verify(stream, never()).closeLocalSide();
        verify(writer).writeHeaders(eq(ctx), eq(promise), eq(STREAM_ID), eq(EMPTY_HEADERS), eq(0),
                eq(DEFAULT_PRIORITY_WEIGHT), eq(false), eq(0), eq(false), eq(false));
    }

    @Test
    public void headersWriteShouldClosePushStream() throws Exception {
        when(stream.state()).thenReturn(RESERVED_LOCAL).thenReturn(HALF_CLOSED_LOCAL);
        handler.writeHeaders(ctx, promise, STREAM_ID, EMPTY_HEADERS, 0, true, false);
        verify(stream).openForPush();
        verify(stream).closeLocalSide();
        verify(writer).writeHeaders(eq(ctx), eq(promise), eq(STREAM_ID), eq(EMPTY_HEADERS), eq(0),
                eq(DEFAULT_PRIORITY_WEIGHT), eq(false), eq(0), eq(true), eq(false));
    }

    @Test(expected = Http2Exception.class)
    public void pushPromiseWriteAfterGoAwayShouldFail() throws Exception {
        when(connection.isGoAway()).thenReturn(true);
        handler.writePushPromise(ctx, promise, STREAM_ID, PUSH_STREAM_ID, EMPTY_HEADERS, 0);
    }

    @Test
    public void pushPromiseWriteShouldReserveStream() throws Exception {
        handler.writePushPromise(ctx, promise, STREAM_ID, PUSH_STREAM_ID, EMPTY_HEADERS, 0);
        verify(local).reservePushStream(eq(PUSH_STREAM_ID), eq(stream));
        verify(writer).writePushPromise(eq(ctx), eq(promise), eq(STREAM_ID), eq(PUSH_STREAM_ID),
                eq(EMPTY_HEADERS), eq(0));
    }

    @Test(expected = Http2Exception.class)
    public void priorityWriteAfterGoAwayShouldFail() throws Exception {
        when(connection.isGoAway()).thenReturn(true);
        handler.writePriority(ctx, promise, STREAM_ID, 0, (short) 255, true);
    }

    @Test
    public void priorityWriteShouldSetPriorityForStream() throws Exception {
        handler.writePriority(ctx, promise, STREAM_ID, 0, (short) 255, true);
        verify(outboundFlow).updateStream(eq(STREAM_ID), eq(0), eq((short) 255), eq(true));
        verify(writer).writePriority(eq(ctx), eq(promise), eq(STREAM_ID), eq(0), eq((short) 255),
                eq(true));
    }

    @Test
    public void rstStreamWriteForUnknownStreamShouldIgnore() throws Exception {
        handler.writeRstStream(ctx, promise, 5, PROTOCOL_ERROR.code());
        verify(writer, never()).writeRstStream(eq(ctx), eq(promise), anyInt(), anyLong());
    }

    @Test
    public void rstStreamWriteShouldCloseStream() throws Exception {
        handler.writeRstStream(ctx, promise, STREAM_ID, PROTOCOL_ERROR.code());
        verify(stream).close();
        verify(writer).writeRstStream(eq(ctx), eq(promise), eq(STREAM_ID),
                eq((long) PROTOCOL_ERROR.code()));
    }

    @Test(expected = Http2Exception.class)
    public void pingWriteAfterGoAwayShouldFail() throws Exception {
        when(connection.isGoAway()).thenReturn(true);
        handler.writePing(ctx, promise, emptyPingBuf());
    }

    @Test
    public void pingWriteShouldSucceed() throws Exception {
        handler.writePing(ctx, promise, emptyPingBuf());
        verify(writer).writePing(eq(ctx), eq(promise), eq(false), eq(emptyPingBuf()));
    }

    @Test(expected = Http2Exception.class)
    public void settingsWriteAfterGoAwayShouldFail() throws Exception {
        when(connection.isGoAway()).thenReturn(true);
        handler.writeSettings(ctx, promise, new Http2Settings());
    }

    @Test
    public void settingsWriteShouldUpdateSettings() throws Exception {
        Http2Settings settings = new Http2Settings();
        settings.allowCompressedData(false);
        settings.initialWindowSize(100);
        settings.pushEnabled(false);
        settings.maxConcurrentStreams(1000);
        settings.maxHeaderTableSize(2000);
        handler.writeSettings(ctx, promise, settings);
        verify(writer).writeSettings(eq(ctx), eq(promise), eq(settings));
        verify(local).allowCompressedData(eq(false));
        verify(inboundFlow).initialInboundWindowSize(eq(100));
        verify(local).allowPushTo(eq(false));
        verify(remote).maxStreams(eq(1000));
        verify(reader).maxHeaderTableSize(2000);
    }

    private ByteBuf dummyData() {
        // The buffer is purposely 8 bytes so it will even work for a ping frame.
        return Unpooled.copiedBuffer("abcdefgh", UTF_8);
    }

    private void mockContext() {
        Mockito.reset(ctx);
        when(ctx.channel()).thenReturn(channel);
        when(ctx.newSucceededFuture()).thenReturn(future);
        when(ctx.newPromise()).thenReturn(promise);
    }

    /**
     * Calls the decode method on the handler and gets back the captured internal observer
     */
    private Http2FrameObserver decode() throws Exception {
        ArgumentCaptor<Http2FrameObserver> internalObserver =
                ArgumentCaptor.forClass(Http2FrameObserver.class);
        doNothing().when(reader).readFrame(any(ByteBufAllocator.class), any(ByteBuf.class),
                internalObserver.capture());
        handler.decode(ctx, EMPTY_BUFFER, Collections.emptyList());
        return internalObserver.getValue();
    }
}
