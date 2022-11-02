package io.netty.netty.codec;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.FixedLengthFrameDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.nio.charset.StandardCharsets;

/**
 * @author lxcecho 909231497@qq.com
 * @since 10:49 06-08-2022
 */
public class DecoderTest02 {

    public static void main(String[] args) throws Exception {
        new DecoderTest02().startEchoServer(8088);
    }

    public void startEchoServer(int port) throws Exception {
        EventLoopGroup boss = new NioEventLoopGroup();
        EventLoopGroup worker = new NioEventLoopGroup();

        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();

            serverBootstrap.group(boss, worker)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO)) // 设置ServerSocketChannel 对应的 Handler
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception { // 设置 SocketChannel 对应的 Handler
                            ByteBuf delimiter = Unpooled.copiedBuffer("&".getBytes());
                            ch.pipeline()
                                    .addLast(new FixedLengthFrameDecoder(10))
                                    .addLast(new ResponseSampleEncoder())
                                    .addLast(new RequestSampleHandler());

                        }
                    });
            // bind() 才是真正进行服务器端口绑定和启动的入口，sync() 表示阻塞等待服务器启动完成。
            ChannelFuture channelFuture = serverBootstrap.bind(port).sync();
            channelFuture.channel().closeFuture().sync();
        } finally {
            worker.shutdownGracefully();
            boss.shutdownGracefully();
        }
    }

    @ChannelHandler.Sharable
    public static class ResponseSampleEncoder extends MessageToByteEncoder<ResponseSample> {
        @Override
        protected void encode(ChannelHandlerContext ctx, ResponseSample msg, ByteBuf out) throws Exception {
            if (msg != null) {
                out.writeBytes(msg.getCode().getBytes());
                out.writeBytes(msg.getData().getBytes());
                out.writeLong(msg.getTimestamp());
            }
        }
    }

    public static  class RequestSampleHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            String content = ((ByteBuf) msg).toString(StandardCharsets.UTF_8);
            ResponseSample response = new ResponseSample("OK", content, System.currentTimeMillis());
            ctx.channel().writeAndFlush(response);
        }
    }

    @Getter
    @Setter
    @AllArgsConstructor
    public static class ResponseSample {

        private String code;

        private String data;

        private long timestamp;

    }

}
