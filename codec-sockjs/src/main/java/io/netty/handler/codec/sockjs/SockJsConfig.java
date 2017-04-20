/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.sockjs;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import io.netty.handler.codec.sockjs.util.Arguments;
import io.netty.util.internal.StringUtil;

/**
 * Configuration for a SockJS Session.
 */
public final class SockJsConfig {

    private static final int DEFAULT_MAX_STREAMING_BYTES_SIZE = 131072;
    private static final int DEFAULT_SESSION_TIMEOUT = 5000;
    private static final int DEFAULT_HEARTBEAT_INTERVAL = 25000;
    private final String prefix;
    private final boolean webSocketEnabled;
    private final long webSocketHeartbeatInterval;
    private final Set<String> webSocketProtocols;
    private final boolean cookiesNeeded;
    private final String sockjsUrl;
    private final long sessionTimeout;
    private final long heartbeatInterval;
    private final int maxStreamingBytesSize;
    private final boolean tls;
    private final String keyStore;
    private final String keystorePassword;

    SockJsConfig(final Builder builder) {
        prefix = builder.prefix;
        webSocketEnabled = builder.webSocketEnabled;
        webSocketProtocols = builder.webSocketProtocols;
        webSocketHeartbeatInterval = builder.webSocketHeartbeatInterval;
        cookiesNeeded = builder.cookiesNeeded;
        sockjsUrl = builder.sockJsUrl;
        sessionTimeout = builder.sessionTimeout;
        heartbeatInterval = builder.heartbeatInterval;
        maxStreamingBytesSize = builder.maxStreamingBytesSize;
        tls = builder.tls;
        keyStore = builder.keyStore;
        keystorePassword = builder.keyStorePassword;
    }

    /**
     * The prefix/name, of the SockJS service.
     * For example, in the url "http://localhost/echo/111/12345/xhr", 'echo' is the prefix.
     *
     * @return {@code String} the prefix/name of the SockJS service.
     */
    public String prefix() {
        return prefix;
    }

    /**
     * Determines whether WebSocket support will not be enabled.
     *
     * @return {@code true} if WebSocket support is enabled.
     */
    public boolean isWebSocketEnabled() {
        return webSocketEnabled;
    }

    /**
     * The WebSocket heartbeat interval.
     *
     * This might be required in certain environments where idle connections
     * are closed by a proxy. It is a separate value from the hearbeat that
     * the streaming protocols use as it is often desirable to have a much
     * larger value for it.
     *
     * @return {@code long} how often, in ms, that a WebSocket heartbeat should be sent
     */
    public long webSocketHeartbeatInterval() {
        return webSocketHeartbeatInterval;
    }

    /**
     * If WebSockets are in use the this give the oppertunity to specify
     * what 'WebSocket-Protocols' should be returned and supported by this
     * SockJS session.
     *
     * @return {@code Set<String>} of WebSocket protocols supported.
     */
    public Set<String> webSocketProtocols() {
        return webSocketProtocols;
    }

    /**
     * Determines if a {@code JSESSIONID} cookie will be set. This is used by some
     * load balancers to enable session stickyness.
     *
     * @return {@code true} if a {@code JSESSIONID} cookie should be set.
     */
    public boolean areCookiesNeeded() {
        return cookiesNeeded;
    }

    /**
     * The url to the sock-js-<version>.json. This is used by the 'prefix/iframe' protocol and
     * the url is replaced in the script returned to the client. This allows for configuring
     * the version of sockjs used. By default it is 'http://cdn.jsdelivr.net/sockjs/0.3.4/sockjs.min.js'.
     *
     * @return {@code String} the url to the sockjs version to be used.
     */
    public String sockJsUrl() {
        return sockjsUrl;
    }

    /**
     * The session time out in milliseconds.
     *
     * @return {@code long} the timeout in ms. The default is {@value #DEFAULT_SESSION_TIMEOUT} ms.
     */
    public long sessionTimeout() {
        return sessionTimeout;
    }

    /**
     * The heartbeat interval in milliseconds.
     *
     * @return {@code long} how often, in ms, that a heartbeat should be sent.
     *                      The default is {@value #DEFAULT_HEARTBEAT_INTERVAL} ms
     */
    public long heartbeatInterval() {
        return heartbeatInterval;
    }

    /**
     * The max number of types that a streaming transport protocol should allow to be returned
     * before closing the connection, forcing the client to reconnect. This is done so that the
     * responseText in the XHR Object will not grow and be come an issue for the client. Instead,
     * by forcing a reconnect the client will create a new XHR object and this can be see as a
     * form of garbage collection.
     *
     * @return {@code int} the max number of bytes that can be written.
     *                     Default is {@value #DEFAULT_MAX_STREAMING_BYTES_SIZE}.
     */
    public int maxStreamingBytesSize() {
        return maxStreamingBytesSize;
    }

    /**
     * Determines whether transport layer security (TLS) should be used.
     *
     * @return {@code true} if transport layer security should be used.
     */
    public boolean isTls() {
        return tls;
    }

    /**
     * Returns the keystore to be used if transport layer security is enabled.
     *
     * @return {@code String} the path to the keystore to be used
     */
    public String keyStore() {
        return keyStore;
    }

    /**
     * Returns the keystore password to be used if transport layer security is enabled.
     *
     * @return {@code String} the password to the configured keystore
     */
    public String keyStorePassword() {
        return keystorePassword;
    }

    @Override
    public String toString() {
        return new StringBuilder(StringUtil.simpleClassName(this))
                .append("[prefix=").append(prefix)
                .append(", webSocketEnabled=").append(webSocketEnabled)
                .append(", webSocketProtocols=").append(webSocketProtocols)
                .append(", webSocketHeartbeatInterval=").append(webSocketHeartbeatInterval)
                .append(", cookiesNeeded=").append(cookiesNeeded)
                .append(", sockJsUrl=").append(sockjsUrl)
                .append(", sessionTimeout=").append(sessionTimeout)
                .append(", heartbeatInterval=").append(heartbeatInterval)
                .append(", maxStreamingBytesSize=").append(maxStreamingBytesSize)
                .append(", tls=").append(tls)
                .append(", keyStore=").append(keyStore)
                .append(']')
                .toString();
    }

    /**
     * The prefix, or name, of the service.
     * For example, in the url "http://localhost/echo/111/12345/xhr", 'echo' is the prefix.
     *
     * @param prefix the prefix/name of the SockJS service.
     */
    public static Builder withPrefix(final String prefix) {
        Arguments.checkNotNullAndNotEmpty(prefix, "prefix");
        return new Builder(prefix);
    }

    public static class Builder {
        private final String prefix;
        private boolean webSocketEnabled = true;
        private long webSocketHeartbeatInterval = -1;
        private final Set<String> webSocketProtocols = new HashSet<String>();
        private boolean cookiesNeeded;
        private String sockJsUrl = "http://cdn.jsdelivr.net/sockjs/0.3.4/sockjs.min.js" ;
        private long sessionTimeout = DEFAULT_SESSION_TIMEOUT;
        private long heartbeatInterval = DEFAULT_HEARTBEAT_INTERVAL;
        private int maxStreamingBytesSize = DEFAULT_MAX_STREAMING_BYTES_SIZE;
        private boolean tls;
        private String keyStore;
        private String keyStorePassword;

        /**
         * The prefix, or name, of the service.
         * For example, in the url "http://localhost/echo/111/12345/xhr", 'echo' is the prefix.
         *
         * @param prefix the prefix/name of the SockJS service.
         */
        public Builder(final String prefix) {
            this.prefix = prefix;
        }

        /**
         * Will disable WebSocket suppport.
         */
        public Builder disableWebSocket() {
            webSocketEnabled = false;
            return this;
        }

        /**
         * Specifies a heartbeat interval for SockJS WebSocket transport.
         * This might be required in certain environments where idle connections
         * are closed by a proxy. It is a separate value from the hearbeat that
         * the streaming protocols use as it is often desirable to have a mush
         * larger value for it.
         *
         * @param ms how often that a WebSocket heartbeat should be sent
         */
        public Builder webSocketHeartbeatInterval(final long ms) {
            webSocketHeartbeatInterval = ms;
            return this;
        }

        /**
         * Adds the given protocols which will be returned to during the
         * Http upgrade request as the header 'WebSocket-Protocol'.
         *
         * @param protocols the protocols that are supported.
         */
        public Builder webSocketProtocols(final String... protocols) {
            webSocketProtocols.addAll(Arrays.asList(protocols));
            return this;
        }

        /**
         * Determines if a {@code JSESSIONID} cookie will be set. This is used by some
         * load balancers to enable session stickyness.
         */
        public Builder cookiesNeeded() {
            cookiesNeeded = true;
            return this;
        }

        /**
         * The url to the sock-js-<version>.json. This is used by the 'prefix/iframe' protocol and
         * the url is replaced in the script returned to the client. This allows for configuring
         * the version of sockjs used. By default it is 'http://cdn.jsdelivr.net/sockjs/0.3.4/sockjs.min.js'.
         *
         * @param sockJsUrl the url to the sockjs version to be used.
         */
        public Builder sockJsUrl(final String sockJsUrl) {
            this.sockJsUrl = sockJsUrl;
            return this;
        }

        /**
         * The session time out in milliseconds.
         *
         * @return {@code long} the timeout in ms. The default is {@value #DEFAULT_SESSION_TIMEOUT} ms.
         */
        public Builder sessionTimeout(final long ms) {
            sessionTimeout = ms;
            return this;
        }

        /**
         * Specifies a heartbeat interval.
         *
         * @param ms how often that a heartbeat should be sent. Default is {@value #DEFAULT_HEARTBEAT_INTERVAL} ms.
         */
        public Builder heartbeatInterval(final long ms) {
            heartbeatInterval = ms;
            return this;
        }

        /**
         * The max number of types that a streaming transport protocol should allow to be returned
         * before closing the connection, forcing the client to reconnect. This is done so that the
         * responseText in the XHR Object will not grow and be come an issue for the client. Instead,
         * by forcing a reconnect the client will create a new XHR object and this can be see as a
         * form of garbage collection.
         *
         * @param max the max number of bytes that can be written.
         *            Default is {@value #DEFAULT_MAX_STREAMING_BYTES_SIZE}.
         */
        public Builder maxStreamingBytesSize(final int max) {
            maxStreamingBytesSize = max;
            return this;
        }

        /**
         * Determines whether transport layer security (TLS) should be used.
         *
         * @param tls if transport layer security should be used.
         */
        public Builder tls(final boolean tls) {
            this.tls = tls;
            return this;
        }

        /**
         * Specifies the keystore to be used if transport layer security (TLS) is enabled.
         *
         * @param keyStore the keystore to be used when TLS is enabled.
         */
        public Builder keyStore(final String keyStore) {
            this.keyStore = keyStore;
            return this;
        }

        /**
         * Specifies the keystore password to be used if transport layer security (TLS) is enabled.
         *
         * @param password the keystore password to be used when TLS is enabled.
         */
        public Builder keyStorePassword(final String password) {
            keyStorePassword = password;
            return this;
        }

        /**
         * Builds Config with the previously set values.
         *
         * @return {@link SockJsConfig} the configuration for the SockJS service.
         */
        public SockJsConfig build() {
            if (tls && (keyStore == null || keyStorePassword == null)) {
                throw new IllegalStateException("keyStore and keyStorePassword must be specified if 'tls' is enabled");
            }
            return new SockJsConfig(this);
        }
    }

}

