package io.netty.handler.codec.http.websocketx;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpHeaders;

import java.net.URI;

/**
 * This handler does all the heavy lifting for you to run a websocket client.
 *
 * It takes care of websocket handshaking as well as processing of Ping, Pong frames. Text and Binary
 * data frames are passed to the next handler in the pipeline (implemented by you) for processing.
 * Also the close frame is passed to the next handler as you may want inspect it before close the connection if
 * the {@code handleCloseFrames} is {@code false}, default is {@code true}.
 *
 * This implementation will establish the websocket connection once the connection to the remote server was complete.
 */
public class WebSocketClientProtocolHandler extends WebSocketProtocolHandler {

    private final WebSocketClientHandshaker handshaker;
    private final boolean handleCloseFrames;

    /**
     * Base constructor
     *
     * @param webSocketURL
     *            URL for web socket communications. e.g "ws://myhost.com/mypath". Subsequent web socket frames will be
     *            sent to this URL.
     * @param version
     *            Version of web socket specification to use to connect to the server
     * @param subprotocol
     *            Sub protocol request sent to the server.
     * @param customHeaders
     *            Map of custom headers to add to the client request
     * @param maxFramePayloadLength
     *            Maximum length of a frame's payload
     * @param handleCloseFrames
     *            {@code true} if close frames should not be forwarded and just close the channel
     */
    public WebSocketClientProtocolHandler(URI webSocketURL, WebSocketVersion version, String subprotocol,
                                                   boolean allowExtensions, HttpHeaders customHeaders,
                                                   int maxFramePayloadLength, boolean handleCloseFrames) {
        this(WebSocketClientHandshakerFactory.newHandshaker(webSocketURL, version, subprotocol,
                allowExtensions, customHeaders, maxFramePayloadLength), handleCloseFrames);
    }

    /**
     * Base constructor
     *
     * @param webSocketURL
     *            URL for web socket communications. e.g "ws://myhost.com/mypath". Subsequent web socket frames will be
     *            sent to this URL.
     * @param version
     *            Version of web socket specification to use to connect to the server
     * @param subprotocol
     *            Sub protocol request sent to the server.
     * @param customHeaders
     *            Map of custom headers to add to the client request
     * @param maxFramePayloadLength
     *            Maximum length of a frame's payload
     */
    public WebSocketClientProtocolHandler(URI webSocketURL, WebSocketVersion version, String subprotocol,
                                          boolean allowExtensions, HttpHeaders customHeaders,
                                          int maxFramePayloadLength) {
        this(webSocketURL, version, subprotocol,
                allowExtensions, customHeaders, maxFramePayloadLength, true);
    }

    /**
     * Base constructor
     *
     * @param handshaker
     *            The {@link WebSocketClientHandshaker} which will be used to issue the handshake once the connection
     *            was established to the remote peer.
     * @param handleCloseFrames
     *            {@code true} if close frames should not be forwarded and just close the channel
     */
    public WebSocketClientProtocolHandler(WebSocketClientHandshaker handshaker, boolean handleCloseFrames) {
        this.handshaker = handshaker;
        this.handleCloseFrames = handleCloseFrames;
    }

    /**
     * Base constructor
     *
     * @param handshaker
     *            The {@link WebSocketClientHandshaker} which will be used to issue the handshake once the connection
     *            was established to the remote peer.
     */
    public WebSocketClientProtocolHandler(WebSocketClientHandshaker handshaker) {
        this(handshaker, true);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        if (handleCloseFrames && frame instanceof CloseWebSocketFrame) {
            ctx.close();
            return;
        }
        super.messageReceived(ctx, frame);
    }

    @Override
    public void afterAdd(ChannelHandlerContext ctx) {
        ChannelPipeline cp = ctx.pipeline();
        if (cp.get(WebSocketClientProtocolHandshakeHandler.class) == null) {
            // Add the WebSocketClientProtocolHandshakeHandler before this one.
            ctx.pipeline().addBefore(ctx.name(), WebSocketClientProtocolHandshakeHandler.class.getName(),
                    new WebSocketClientProtocolHandshakeHandler(handshaker));
        }
    }
}
