/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.http;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.compression.BrotliEncoder;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.compression.ZlibWrapper;

/**
 * Compresses an {@link HttpMessage} and an {@link HttpContent} in {@code gzip} or
 * {@code deflate} encoding while respecting the {@code "Accept-Encoding"} header.
 * If there is no matching encoding, no compression is done.  For more
 * information on how this handler modifies the message, please refer to
 * {@link HttpContentEncoder}.
 */
public class HttpContentCompressor extends HttpContentEncoder {

    private final int brotliCompressionQuality;
    private final int compressionLevel;
    private final int windowBits;
    private final int memLevel;
    private final int contentSizeThreshold;
    private ChannelHandlerContext ctx;

    /**
     * Creates a new handler with the default compression level (<tt>6</tt>),
     * default window size (<tt>15</tt>) and default memory level (<tt>8</tt>).
     */
    public HttpContentCompressor() {
        this(6);
    }

    /**
     * Creates a new handler with the specified compression level, default
     * window size (<tt>15</tt>) and default memory level (<tt>8</tt>).
     *
     * @param compressionLevel {@code 1} yields the fastest compression and {@code 9} yields the
     *                         best compression.  {@code 0} means no compression.  The default
     *                         compression level is {@code 6}.
     */
    public HttpContentCompressor(int compressionLevel) {
        this(compressionLevel, 15, 8, 0);
    }

    /**
     * Creates a new handler with the specified compression level, default
     * window size (<tt>15</tt>) and default memory level (<tt>8</tt>).
     *
     * @param compressionLevel {@code 1} yields the fastest compression and {@code 9} yields the
     *                         best compression.  {@code 0} means no compression.  The default
     *                         compression level is {@code 6}.
     * @param brotliCompressionQuality Brotli Compression Quality. The default compression quality is {@code 4}.
     */
    public HttpContentCompressor(int compressionLevel, int brotliCompressionQuality) {
        this(compressionLevel, 15, 8, 0, brotliCompressionQuality);
    }

    /**
     * Creates a new handler with the specified compression level, window size,
     * and memory level..
     *
     * @param compressionLevel {@code 1} yields the fastest compression and {@code 9} yields the
     *                         best compression.  {@code 0} means no compression.  The default
     *                         compression level is {@code 6}.
     * @param windowBits       The base two logarithm of the size of the history buffer.  The
     *                         value should be in the range {@code 9} to {@code 15} inclusive.
     *                         Larger values result in better compression at the expense of
     *                         memory usage.  The default value is {@code 15}.
     * @param memLevel         How much memory should be allocated for the internal compression
     *                         state.  {@code 1} uses minimum memory and {@code 9} uses maximum
     *                         memory.  Larger values result in better and faster compression
     *                         at the expense of memory usage.  The default value is {@code 8}
     */
    public HttpContentCompressor(int compressionLevel, int windowBits, int memLevel) {
        this(compressionLevel, windowBits, memLevel, 0);
    }

    /**
     * Creates a new handler with the specified compression level, window size,
     * and memory level..
     *
     * @param compressionLevel     {@code 1} yields the fastest compression and {@code 9} yields the
     *                             best compression.  {@code 0} means no compression.  The default
     *                             compression level is {@code 6}.
     * @param windowBits           The base two logarithm of the size of the history buffer.  The
     *                             value should be in the range {@code 9} to {@code 15} inclusive.
     *                             Larger values result in better compression at the expense of
     *                             memory usage.  The default value is {@code 15}.
     * @param memLevel             How much memory should be allocated for the internal compression
     *                             state.  {@code 1} uses minimum memory and {@code 9} uses maximum
     *                             memory.  Larger values result in better and faster compression
     *                             at the expense of memory usage.  The default value is {@code 8}
     * @param contentSizeThreshold The response body is compressed when the size of the response
     *                             body exceeds the threshold. The value should be a non negative
     *                             number. {@code 0} will enable compression for all responses.
     */
    public HttpContentCompressor(int compressionLevel, int windowBits, int memLevel, int contentSizeThreshold) {
        this(compressionLevel, windowBits, memLevel, contentSizeThreshold, 4);
    }

    /**
     * Creates a new handler with the specified compression level, window size,
     * and memory level..
     *
     * @param compressionLevel     {@code 1} yields the fastest compression and {@code 9} yields the
     *                             best compression.  {@code 0} means no compression.  The default
     *                             compression level is {@code 6}.
     * @param windowBits           The base two logarithm of the size of the history buffer.  The
     *                             value should be in the range {@code 9} to {@code 15} inclusive.
     *                             Larger values result in better compression at the expense of
     *                             memory usage.  The default value is {@code 15}.
     * @param memLevel             How much memory should be allocated for the internal compression
     *                             state.  {@code 1} uses minimum memory and {@code 9} uses maximum
     *                             memory.  Larger values result in better and faster compression
     *                             at the expense of memory usage.  The default value is {@code 8}
     * @param contentSizeThreshold The response body is compressed when the size of the response
     *                             body exceeds the threshold. The value should be a non negative
     *                             number. {@code 0} will enable compression for all responses.
     * @param brotliCompressionQuality Brotli Compression Quality. The default compression quality is {@code 4}.
     */
    public HttpContentCompressor(int compressionLevel, int windowBits, int memLevel, int contentSizeThreshold,
                                 int brotliCompressionQuality) {
        if (compressionLevel < 0 || compressionLevel > 9) {
            throw new IllegalArgumentException("compressionLevel: " + compressionLevel + " (expected: 0-9)");
        }
        if (windowBits < 9 || windowBits > 15) {
            throw new IllegalArgumentException("windowBits: " + windowBits + " (expected: 9-15)");
        }
        if (memLevel < 1 || memLevel > 9) {
            throw new IllegalArgumentException("memLevel: " + memLevel + " (expected: 1-9)");
        }
        if (contentSizeThreshold < 0) {
            throw new IllegalArgumentException("contentSizeThreshold: " + contentSizeThreshold +
                    " (expected: non negative number)");
        }
        if (brotliCompressionQuality < 1 || brotliCompressionQuality > 11) {
            throw new IllegalArgumentException("brotliCompressionQuality: " + compressionLevel + " (expected: 1-11)");
        }
        this.compressionLevel = compressionLevel;
        this.windowBits = windowBits;
        this.memLevel = memLevel;
        this.contentSizeThreshold = contentSizeThreshold;
        this.brotliCompressionQuality = brotliCompressionQuality;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
    }

    @Override
    protected Result beginEncode(HttpResponse httpResponse, String acceptEncoding) throws Exception {
        if (this.contentSizeThreshold > 0) {
            if (httpResponse instanceof HttpContent &&
                    ((HttpContent) httpResponse).content().readableBytes() < contentSizeThreshold) {
                return null;
            }
        }

        String contentEncoding = httpResponse.headers().get(HttpHeaderNames.CONTENT_ENCODING);
        if (contentEncoding != null) {
            // Content-Encoding was set, either as something specific or as the IDENTITY encoding
            // Therefore, we should NOT encode here
            return null;
        }

        String targetContentEncoding = determineEncoding(acceptEncoding);
        if (targetContentEncoding == null) {
            return null;
        }

        ChannelHandler compressor;
        if (targetContentEncoding.equals("gzip")) {
            compressor = ZlibCodecFactory.newZlibEncoder(ZlibWrapper.GZIP, compressionLevel, windowBits, memLevel);
        } else if (targetContentEncoding.equals("deflate")) {
            compressor = ZlibCodecFactory.newZlibEncoder(ZlibWrapper.GZIP, compressionLevel, windowBits, memLevel);
        } else if (targetContentEncoding.equals("br")) {
            compressor = new BrotliEncoder(brotliCompressionQuality);
        } else {
            throw new Error();
        }

        return new Result(targetContentEncoding, new EmbeddedChannel(ctx.channel().id(),
                ctx.channel().metadata().hasDisconnect(), ctx.channel().config(), compressor));
    }

    @SuppressWarnings("FloatingPointEquality")
    protected ZlibWrapper determineWrapper(String acceptEncoding) {
        float starQ = -1.0f;
        float gzipQ = -1.0f;
        float deflateQ = -1.0f;
        for (String encoding : acceptEncoding.split(",")) {
            float q = 1.0f;
            int equalsPos = encoding.indexOf('=');
            if (equalsPos != -1) {
                try {
                    q = Float.parseFloat(encoding.substring(equalsPos + 1));
                } catch (NumberFormatException e) {
                    // Ignore encoding
                    q = 0.0f;
                }
            }
            if (encoding.contains("*")) {
                starQ = q;
            } else if (encoding.contains("gzip") && q > gzipQ) {
                gzipQ = q;
            } else if (encoding.contains("deflate") && q > deflateQ) {
                deflateQ = q;
            }
        }
        if (gzipQ > 0.0f || deflateQ > 0.0f) {
            if (gzipQ >= deflateQ) {
                return ZlibWrapper.GZIP;
            } else {
                return ZlibWrapper.ZLIB;
            }
        }
        if (starQ > 0.0f) {
            if (gzipQ == -1.0f) {
                return ZlibWrapper.GZIP;
            }
            if (deflateQ == -1.0f) {
                return ZlibWrapper.ZLIB;
            }
        }
        return null;
    }

    @SuppressWarnings("FloatingPointEquality")
    protected String determineEncoding(String acceptEncoding) {
        float starQ = -1.0f;
        float brQ = -1.0f;
        float gzipQ = -1.0f;
        float deflateQ = -1.0f;
        for (String encoding : acceptEncoding.split(",")) {
            float q = 1.0f;
            int equalsPos = encoding.indexOf('=');
            if (equalsPos != -1) {
                try {
                    q = Float.parseFloat(encoding.substring(equalsPos + 1));
                } catch (NumberFormatException e) {
                    // Ignore encoding
                    q = 0.0f;
                }
            }
            if (encoding.contains("*")) {
                starQ = q;
            } else if (encoding.contains("br") && q > brQ) {
                brQ = q;
            } else if (encoding.contains("gzip") && q > gzipQ) {
                gzipQ = q;
            } else if (encoding.contains("deflate") && q > deflateQ) {
                deflateQ = q;
            }
        }
        if (brQ > 0.0f || gzipQ > 0.0f || deflateQ > 0.0f) {
            if (brQ >= gzipQ) {
                return "br";
            } else if (gzipQ >= deflateQ) {
                return "gzip";
            } else {
                return "deflate";
            }
        }
        if (starQ > 0.0f) {
            if (brQ == -1.0f) {
                return "br";
            }
            if (gzipQ == -1.0f) {
                return "gzip";
            }
            if (deflateQ == -1.0f) {
                return "deflate";
            }
        }
        return null;
    }
}
