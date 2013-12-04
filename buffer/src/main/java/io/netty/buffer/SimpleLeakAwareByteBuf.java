/*
 * Copyright 2013 The Netty Project
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

package io.netty.buffer;

import io.netty.util.ResourceLeak;

final class SimpleLeakAwareByteBuf extends WrappedByteBuf {

    private final ResourceLeak leak;

    SimpleLeakAwareByteBuf(ByteBuf buf, ResourceLeak leak) {
        super(buf);
        this.leak = leak;
    }

    @Override
    public boolean release() {
        boolean deallocated =  super.release();
        if (deallocated) {
            leak.close();
        }
        return deallocated;
    }

    @Override
    public boolean release(int decrement) {
        boolean deallocated = super.release(decrement);
        if (deallocated) {
            leak.close();
        }
        return deallocated;
    }
}
