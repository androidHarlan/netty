/*
 * Copyright 2017 The Netty Project
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
package io.netty5.handler.ssl;

import io.netty5.util.internal.UnstableApi;

/**
 * Event that is fired once we did a selection of a {@link SslContext} based on the {@code SNI hostname},
 * which may be because it was successful or there was an error.
 */
@UnstableApi
public final class SniCompletionEvent extends SslCompletionEvent<String> {
    public SniCompletionEvent(String hostname) {
        super(hostname);
    }

    public SniCompletionEvent(String hostname, Throwable cause) {
        super(hostname, cause);
    }

    public SniCompletionEvent(Throwable cause) {
        this(null, cause);
    }

    /**
     * Returns the SNI hostname send by the client if we were able to parse it, {@code null} otherwise.
     */
    @Override
    public String data() {
        return super.data();
    }

    @Override
    public String toString() {
        final Throwable cause = cause();
        return cause == null ? getClass().getSimpleName() + "(SUCCESS='"  + data() + "'\")":
                getClass().getSimpleName() +  '(' + cause + ')';
    }
}
