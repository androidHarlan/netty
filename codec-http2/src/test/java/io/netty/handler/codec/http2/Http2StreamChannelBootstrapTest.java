package io.netty.handler.codec.http2;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultEventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.hamcrest.core.IsInstanceOf;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

import static io.netty.handler.codec.http2.Http2FrameCodecBuilder.forClient;
import static io.netty.handler.codec.http2.Http2FrameCodecBuilder.forServer;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class Http2StreamChannelBootstrapTest {

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(Http2StreamChannelBootstrapTest.class);

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private volatile Channel serverConnectedChannel;

    @Test
    public void testStreamIsNotCreatedIfParentConnectionIsClosedConcurrently() throws Exception {
        EventLoopGroup group = null;
        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            final CountDownLatch serverChannelLatch = new CountDownLatch(1);
            group = new DefaultEventLoop();
            LocalAddress serverAddress = new LocalAddress(getClass().getName());
            ServerBootstrap sb = new ServerBootstrap()
                    .channel(LocalServerChannel.class)
                    .group(group)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            serverConnectedChannel = ch;
                            ch.pipeline().addLast(forServer().build(), newMultiplexedHandler());
                            serverChannelLatch.countDown();
                        }
                    });
            serverChannel = sb.bind(serverAddress).sync().channel();

            Bootstrap cb = new Bootstrap()
                    .channel(LocalChannel.class)
                    .group(group)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast(forClient().build(), newMultiplexedHandler());
                        }
                    });
            clientChannel = cb.connect(serverAddress).sync().channel();
            assertTrue(serverChannelLatch.await(3, SECONDS));

            final CountDownLatch closeLatch = new CountDownLatch(1);
            final Channel clientChannelToClose = clientChannel;
            group.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        closeLatch.await();
                        clientChannelToClose.close().syncUninterruptibly();
                    } catch (InterruptedException e) {
                        logger.error(e);
                    }
                }
            });

            Http2StreamChannelBootstrap bootstrap = new Http2StreamChannelBootstrap(clientChannel);
            Promise<Http2StreamChannel> promise = clientChannel.eventLoop().newPromise();
            bootstrap.open(promise);
            assertThat(promise.isDone(), is(false));
            closeLatch.countDown();

            exceptionRule.expect(ExecutionException.class);
            exceptionRule.expectCause(IsInstanceOf.<Throwable>instanceOf(ClosedChannelException.class));
            promise.get(3, SECONDS);
        } finally {
            safeClose(clientChannel);
            safeClose(serverConnectedChannel);
            safeClose(serverChannel);
            if (group != null) {
                group.shutdownGracefully(0, 3, SECONDS);
            }
        }
    }

    private static Http2MultiplexHandler newMultiplexedHandler() {
        return new Http2MultiplexHandler(new ChannelInitializer<Http2StreamChannel>() {
            @Override
            protected void initChannel(Http2StreamChannel ch) {
                // noop
            }
        });
    }

    private static void safeClose(Channel channel) {
        if (channel != null) {
            try {
                channel.close().syncUninterruptibly();
            } catch (Exception e) {
                logger.error(e);
            }
        }
    }
}
