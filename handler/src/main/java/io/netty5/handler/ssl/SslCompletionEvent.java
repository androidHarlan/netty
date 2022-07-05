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

import io.netty5.handler.codec.ProtocolEvent;

import static java.util.Objects.requireNonNull;

public abstract class SslCompletionEvent<V> implements ProtocolEvent<V> {

    private final V data;
    private final Throwable cause;

    SslCompletionEvent(V data) {
        this.data = data;
        cause = null;
    }

    SslCompletionEvent(V data, Throwable cause) {
        this.data = data;
        this.cause = requireNonNull(cause, "cause");
    }

    @Override
    public V data() {
        return data;
    }

    /**
     * Return the {@link Throwable} if {@link #isSuccess()} returns {@code false}
     * and so the completion failed.
     */
    public final Throwable cause() {
        return cause;
    }

    @Override
    public String toString() {
        final Throwable cause = cause();
        return cause == null? getClass().getSimpleName() + "(SUCCESS)" :
                getClass().getSimpleName() +  '(' + cause + ')';
    }
}
