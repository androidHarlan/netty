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

import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT;
import static io.netty.handler.codec.http2.Http2TestUtil.as;
import static io.netty.handler.codec.http2.Http2TestUtil.runInChannel;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.AsciiString;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http2.Http2TestUtil.FrameAdapter;
import io.netty.handler.codec.http2.Http2TestUtil.Http2Runnable;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.Future;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Test for data decompression in the HTTP/2 codec.
 */
public class DataCompressionHttp2Test {
    private static final AsciiString GET = as("GET");
    private static final AsciiString POST = as("POST");
    private static final AsciiString PATH = as("/some/path");
    private List<ByteBuf> dataCapture;

    @Mock
    private Http2FrameListener serverListener;
    @Mock
    private Http2FrameListener clientListener;
    @Mock
    private Http2LifecycleManager serverLifeCycleManager;
    @Mock
    private Http2LifecycleManager clientLifeCycleManager;

    private ByteBufAllocator alloc;
    private Http2ConnectionEncoder serverEncoder;
    private Http2ConnectionEncoder clientEncoder;
    private ServerBootstrap sb;
    private Bootstrap cb;
    private Channel serverChannel;
    private Channel serverConnectedChannel;
    private Channel clientChannel;
    private volatile CountDownLatch serverLatch;
    private volatile CountDownLatch clientLatch;
    private FrameAdapter serverAdapter;
    private FrameAdapter clientAdapter;
    private Http2Connection serverConnection;

    @Before
    public void setup() throws InterruptedException {
        MockitoAnnotations.initMocks(this);
    }

    @After
    public void teardown() throws InterruptedException {
        if (dataCapture != null) {
            for (int i = 0; i < dataCapture.size(); ++i) {
                dataCapture.get(i).release();
            }
            dataCapture = null;
        }
        serverChannel.close().sync();
        Future<?> serverGroup = sb.group().shutdownGracefully(0, 0, MILLISECONDS);
        Future<?> serverChildGroup = sb.childGroup().shutdownGracefully(0, 0, MILLISECONDS);
        Future<?> clientGroup = cb.group().shutdownGracefully(0, 0, MILLISECONDS);
        serverGroup.sync();
        serverChildGroup.sync();
        clientGroup.sync();
        serverAdapter = null;
        clientAdapter = null;
        serverConnection = null;
    }

    @Test
    public void justHeadersNoData() throws Exception {
        bootstrapEnv(1, 1);
        final Http2Headers headers = new DefaultHttp2Headers().method(GET).path(PATH)
                .set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.GZIP);
        // Required because the decompressor intercepts the onXXXRead events before
        // our {@link Http2TestUtil$FrameAdapter} does.
        FrameAdapter.getOrCreateStream(serverConnection, 3, false);
        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() {
                clientEncoder.writeHeaders(ctxClient(), 3, headers, 0, true, newPromiseClient());
                ctxClient().flush();
            }
        });
        awaitServer();
        verify(serverListener).onHeadersRead(any(ChannelHandlerContext.class), eq(3), eq(headers), eq(0),
                eq(DEFAULT_PRIORITY_WEIGHT), eq(false), eq(0), eq(true));
    }

    @Test
    public void gzipEncodingSingleEmptyMessage() throws Exception {
        bootstrapEnv(2, 1);
        final String text = "";
        final ByteBuf data = Unpooled.copiedBuffer(text.getBytes());
        final EmbeddedChannel encoder = new EmbeddedChannel(ZlibCodecFactory.newZlibEncoder(ZlibWrapper.GZIP));
        try {
            final ByteBuf encodedData = encodeData(data, encoder);
            final Http2Headers headers = new DefaultHttp2Headers().method(POST).path(PATH)
                    .set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.GZIP);
            // Required because the decompressor intercepts the onXXXRead events before
            // our {@link Http2TestUtil$FrameAdapter} does.
            FrameAdapter.getOrCreateStream(serverConnection, 3, false);
            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() {
                    clientEncoder.writeHeaders(ctxClient(), 3, headers, 0, false, newPromiseClient());
                    clientEncoder.writeData(ctxClient(), 3, encodedData, 0, true, newPromiseClient());
                    ctxClient().flush();
                }
            });
            awaitServer();
            data.resetReaderIndex();
            ArgumentCaptor<ByteBuf> dataCaptor = ArgumentCaptor.forClass(ByteBuf.class);
            verify(serverListener).onDataRead(any(ChannelHandlerContext.class), eq(3), dataCaptor.capture(), eq(0),
                    eq(true));
            dataCapture = dataCaptor.getAllValues();
            assertEquals(data, dataCapture.get(0));
        } finally {
            data.release();
            cleanupEncoder(encoder);
        }
    }

    @Test
    public void gzipEncodingSingleMessage() throws Exception {
        bootstrapEnv(2, 1);
        final String text = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaabbbbbbbbbbbbbbbbbbbbbbbbbbbbbccccccccccccccccccccccc";
        final ByteBuf data = Unpooled.copiedBuffer(text.getBytes());
        final EmbeddedChannel encoder = new EmbeddedChannel(ZlibCodecFactory.newZlibEncoder(ZlibWrapper.GZIP));
        try {
            final ByteBuf encodedData = encodeData(data, encoder);
            final Http2Headers headers = new DefaultHttp2Headers().method(POST).path(PATH)
                    .set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.GZIP);
            // Required because the decompressor intercepts the onXXXRead events before
            // our {@link Http2TestUtil$FrameAdapter} does.
            FrameAdapter.getOrCreateStream(serverConnection, 3, false);
            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() {
                    clientEncoder.writeHeaders(ctxClient(), 3, headers, 0, false, newPromiseClient());
                    clientEncoder.writeData(ctxClient(), 3, encodedData, 0, true, newPromiseClient());
                    ctxClient().flush();
                }
            });
            awaitServer();
            data.resetReaderIndex();
            ArgumentCaptor<ByteBuf> dataCaptor = ArgumentCaptor.forClass(ByteBuf.class);
            verify(serverListener).onDataRead(any(ChannelHandlerContext.class), eq(3), dataCaptor.capture(), eq(0),
                    eq(true));
            dataCapture = dataCaptor.getAllValues();
            assertEquals(data, dataCapture.get(0));
        } finally {
            data.release();
            cleanupEncoder(encoder);
        }
    }

    @Test
    public void gzipEncodingMultipleMessages() throws Exception {
        bootstrapEnv(3, 1);
        final String text1 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaabbbbbbbbbbbbbbbbbbbbbbbbbbbbbccccccccccccccccccccccc";
        final String text2 = "dddddddddddddddddddeeeeeeeeeeeeeeeeeeeffffffffffffffffffff";
        final ByteBuf data1 = Unpooled.copiedBuffer(text1.getBytes());
        final ByteBuf data2 = Unpooled.copiedBuffer(text2.getBytes());
        final EmbeddedChannel encoder = new EmbeddedChannel(ZlibCodecFactory.newZlibEncoder(ZlibWrapper.GZIP));
        try {
            final ByteBuf encodedData1 = encodeData(data1, encoder);
            final ByteBuf encodedData2 = encodeData(data2, encoder);
            final Http2Headers headers = new DefaultHttp2Headers().method(POST).path(PATH)
                    .set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.GZIP);
            // Required because the decompressor intercepts the onXXXRead events before
            // our {@link Http2TestUtil$FrameAdapter} does.
            FrameAdapter.getOrCreateStream(serverConnection, 3, false);
            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() {
                    clientEncoder.writeHeaders(ctxClient(), 3, headers, 0, false, newPromiseClient());
                    clientEncoder.writeData(ctxClient(), 3, encodedData1, 0, false, newPromiseClient());
                    clientEncoder.writeData(ctxClient(), 3, encodedData2, 0, true, newPromiseClient());
                    ctxClient().flush();
                }
            });
            awaitServer();
            data1.resetReaderIndex();
            data2.resetReaderIndex();
            ArgumentCaptor<ByteBuf> dataCaptor = ArgumentCaptor.forClass(ByteBuf.class);
            ArgumentCaptor<Boolean> endStreamCaptor = ArgumentCaptor.forClass(Boolean.class);
            verify(serverListener, times(2)).onDataRead(any(ChannelHandlerContext.class), eq(3), dataCaptor.capture(),
                    eq(0), endStreamCaptor.capture());
            dataCapture = dataCaptor.getAllValues();
            assertEquals(data1, dataCapture.get(0));
            assertEquals(data2, dataCapture.get(1));
            List<Boolean> endStreamCapture = endStreamCaptor.getAllValues();
            assertFalse(endStreamCapture.get(0));
            assertTrue(endStreamCapture.get(1));
        } finally {
            data1.release();
            data2.release();
            cleanupEncoder(encoder);
        }
    }

    @Test
    public void deflateEncodingSingleLargeMessageReducedWindow() throws Exception {
        bootstrapEnv(3, 1);
        final int BUFFER_SIZE = 1 << 16;
        final ByteBuf data = Unpooled.buffer(BUFFER_SIZE);
        final EmbeddedChannel encoder = new EmbeddedChannel(ZlibCodecFactory.newZlibEncoder(ZlibWrapper.ZLIB));
        try {
            for (int i = 0; i < data.capacity(); ++i) {
                data.writeByte((byte) 'a');
            }
            final ByteBuf encodedData = encodeData(data, encoder);
            final Http2Headers headers = new DefaultHttp2Headers().method(POST).path(PATH)
                    .set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.DEFLATE);
            final Http2Settings settings = new Http2Settings();
            // Assume the compression operation will reduce the size by at least 10 bytes
            settings.initialWindowSize(BUFFER_SIZE - 10);
            runInChannel(serverConnectedChannel, new Http2Runnable() {
                @Override
                public void run() {
                    serverEncoder.writeSettings(ctxServer(), settings, newPromiseServer());
                    ctxServer().flush();
                }
            });
            awaitClient();

            // Required because the decompressor intercepts the onXXXRead events before
            // our {@link Http2TestUtil$FrameAdapter} does.
            FrameAdapter.getOrCreateStream(serverConnection, 3, false);
            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() {
                    clientEncoder.writeSettings(ctxClient(), settings, newPromiseClient());
                    clientEncoder.writeHeaders(ctxClient(), 3, headers, 0, false, newPromiseClient());
                    clientEncoder.writeData(ctxClient(), 3, encodedData, 0, true, newPromiseClient());
                    ctxClient().flush();
                }
            });
            awaitServer();
            data.resetReaderIndex();
            ArgumentCaptor<ByteBuf> dataCaptor = ArgumentCaptor.forClass(ByteBuf.class);
            verify(serverListener).onDataRead(any(ChannelHandlerContext.class), eq(3), dataCaptor.capture(), eq(0),
                    eq(true));
            dataCapture = dataCaptor.getAllValues();
            assertEquals(data, dataCapture.get(0));
        } finally {
            data.release();
            cleanupEncoder(encoder);
        }
    }

    private ByteBuf encodeData(ByteBuf data, EmbeddedChannel encoder) {
        ByteBuf encoded = alloc.buffer(data.readableBytes());
        encoder.writeOutbound(data.retain());
        for (;;) {
            final ByteBuf buf = encoder.readOutbound();
            if (buf == null) {
                break;
            }
            if (!buf.isReadable()) {
                buf.release();
                continue;
            }
            encoded.writeBytes(buf);
            buf.release();
        }
        return encoded;
    }

    private static void cleanupEncoder(EmbeddedChannel encoder) {
        if (encoder.finish()) {
            for (;;) {
                final ByteBuf buf = encoder.readOutbound();
                if (buf == null) {
                    break;
                }
                buf.release();
            }
        }
    }

    private void bootstrapEnv(int serverCountDown, int clientCountDown) throws Exception {
        alloc = UnpooledByteBufAllocator.DEFAULT;
        sb = new ServerBootstrap();
        cb = new Bootstrap();

        serverLatch = new CountDownLatch(serverCountDown);
        clientLatch = new CountDownLatch(clientCountDown);
        serverConnection = new DefaultHttp2Connection(true);

        final CountDownLatch latch = new CountDownLatch(1);
        sb.group(new NioEventLoopGroup(), new NioEventLoopGroup());
        sb.channel(NioServerSocketChannel.class);
        sb.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                CompressorHttp2ConnectionEncoder.Builder builder = new CompressorHttp2ConnectionEncoder.Builder();
                Http2FrameWriter writer = new DefaultHttp2FrameWriter();
                serverEncoder = builder.connection(serverConnection).frameWriter(writer)
                        .outboundFlow(new DefaultHttp2OutboundFlowController(serverConnection, writer))
                        .lifecycleManager(serverLifeCycleManager).build();
                serverAdapter = new FrameAdapter(serverConnection, new DelegatingDecompressorFrameListener(
                        serverConnection, serverListener), serverLatch);
                p.addLast("reader", serverAdapter);
                p.addLast(Http2CodecUtil.ignoreSettingsHandler());
                serverConnectedChannel = ch;
                latch.countDown();
            }
        });

        cb.group(new NioEventLoopGroup());
        cb.channel(NioSocketChannel.class);
        cb.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                Http2Connection connection = new DefaultHttp2Connection(false);
                Http2FrameWriter writer = new DefaultHttp2FrameWriter();
                CompressorHttp2ConnectionEncoder.Builder builder = new CompressorHttp2ConnectionEncoder.Builder();
                clientEncoder = builder.connection(connection).frameWriter(writer)
                        .outboundFlow(new DefaultHttp2OutboundFlowController(connection, writer))
                        .lifecycleManager(clientLifeCycleManager).build();
                clientAdapter = new FrameAdapter(connection, clientListener, clientLatch);
                p.addLast("reader", clientAdapter);
                p.addLast(Http2CodecUtil.ignoreSettingsHandler());
            }
        });

        serverChannel = sb.bind(new InetSocketAddress(0)).sync().channel();
        int port = ((InetSocketAddress) serverChannel.localAddress()).getPort();

        ChannelFuture ccf = cb.connect(new InetSocketAddress(NetUtil.LOCALHOST, port));
        assertTrue(ccf.awaitUninterruptibly().isSuccess());
        clientChannel = ccf.channel();

        assertTrue(latch.await(5, SECONDS));
    }

    private void awaitClient() throws Exception {
        clientLatch.await(5, SECONDS);
    }

    private void awaitServer() throws Exception {
        serverLatch.await(5, SECONDS);
    }

    private ChannelHandlerContext ctxClient() {
        return clientChannel.pipeline().firstContext();
    }

    private ChannelPromise newPromiseClient() {
        return ctxClient().newPromise();
    }

    private ChannelHandlerContext ctxServer() {
        return serverConnectedChannel.pipeline().firstContext();
    }

    private ChannelPromise newPromiseServer() {
        return ctxServer().newPromise();
    }
}
