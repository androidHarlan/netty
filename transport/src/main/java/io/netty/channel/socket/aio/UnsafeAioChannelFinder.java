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
package io.netty.channel.socket.aio;

import io.netty.util.internal.PlatformDependent;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link AioChannelFinder} implementation which uses {@code sun.misc.Unsafe}.
 */
final class UnsafeAioChannelFinder implements AioChannelFinder {
    private static volatile Map<Class<?>, Long> offsetCache = new HashMap<Class<?>, Long>();

    @Override
    public AbstractAioChannel findChannel(Runnable command) throws Exception {
        Long offset;
        for (;;) {
            offset = findFieldOffset(command);
            if (offset == null) {
                return null;
            }
            Object next = PlatformDependent.getObject(command, offset);
            if (next instanceof AbstractAioChannel) {
                return (AbstractAioChannel) next;
            }
            command = (Runnable) next;
        }
    }

    private static Long findFieldOffset(Object command) throws Exception {
        Map<Class<?>, Long> offsetCache = UnsafeAioChannelFinder.offsetCache;
        Class<?> commandType = command.getClass();
        Long res = offsetCache.get(commandType);
        if (res != null) {
            return res;
        }

        for (Field f: commandType.getDeclaredFields()) {
            if (f.getType() == Runnable.class) {
                res = PlatformDependent.objectFieldOffset(f);
                put(offsetCache, commandType, res);
                return res;
            }

            if (f.getType() == Object.class) {
                f.setAccessible(true);
                Object candidate = f.get(command);
                if (candidate instanceof AbstractAioChannel) {
                    res = PlatformDependent.objectFieldOffset(f);
                    put(offsetCache, commandType, res);
                    return res;
                }
            }
        }
        return null;
    }

    private static void put(Map<Class<?>, Long> oldCache, Class<?> key, Long value) {
        Map<Class<?>, Long> newCache = new HashMap<Class<?>, Long>(oldCache.size());
        newCache.putAll(oldCache);
        newCache.put(key, value);
        offsetCache = newCache;
    }
}
