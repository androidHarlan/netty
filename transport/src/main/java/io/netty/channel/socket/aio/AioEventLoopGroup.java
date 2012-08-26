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
package io.netty.channel.socket.aio;

import io.netty.channel.EventExecutor;
import io.netty.channel.EventLoopException;
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.channel.ChannelTaskScheduler;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;
import io.netty.util.internal.DetectionUtil;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.channels.AsynchronousChannelGroup;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import sun.misc.Unsafe;

public class AioEventLoopGroup extends MultithreadEventLoopGroup {
    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(AioEventLoopGroup.class);
    private static final ChannelFinder CHANNEL_FINDER;

    static {
        ChannelFinder finder;
        try {
            // check if Unsafe is present on the classpath
            // and if so try to instance the UnsafeChannelFinder
            // too get the optimal speed.
            if (DetectionUtil.hasUnsafe()) {
                finder = new UnsafeChannelFinder();
            } else {
                finder = new ReflectionChannelFinder();
            }
        } catch (Throwable t) {
            LOGGER.debug("Unable to instance UnsafeChannelFinder fallback to ReflectionChannelFinder", t);
            finder = new ReflectionChannelFinder();
        }
        CHANNEL_FINDER = finder;
    }

    final AsynchronousChannelGroup group;

    public AioEventLoopGroup() {
        this(0);
    }

    public AioEventLoopGroup(int nThreads) {
        this(nThreads, null);
    }

    public AioEventLoopGroup(int nThreads, ThreadFactory threadFactory) {
        super(nThreads, threadFactory);
        try {
            group = AsynchronousChannelGroup.withThreadPool(new AioExecutorService());
        } catch (IOException e) {
            throw new EventLoopException("Failed to create an AsynchronousChannelGroup", e);
        }
    }

    @Override
    protected EventExecutor newChild(
            ThreadFactory threadFactory, ChannelTaskScheduler scheduler, Object... args) throws Exception {
        return new AioEventLoop(this, threadFactory, scheduler);
    }

    private void executeAioTask(Runnable command) {
        AbstractAioChannel ch = null;
        try {
            ch = CHANNEL_FINDER.findChannel(command);
        } catch (Throwable t) {
            // Ignore
        }

        EventExecutor l;
        if (ch != null) {
            l = ch.eventLoop();
        } else {
            l = next();
        }

        if (l.isShutdown()) {
            command.run();
        } else {
            l.execute(command);
        }
    }

    private final class AioExecutorService extends AbstractExecutorService {

        @Override
        public void shutdown() {
            AioEventLoopGroup.this.shutdown();
        }

        @Override
        public List<Runnable> shutdownNow() {
            AioEventLoopGroup.this.shutdown();
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return AioEventLoopGroup.this.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return AioEventLoopGroup.this.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return AioEventLoopGroup.this.awaitTermination(timeout, unit);
        }

        @Override
        public void execute(Runnable command) {
            Class<? extends Runnable> commandType = command.getClass();
            if (commandType.getName().startsWith("sun.nio.ch.")) {
                executeAioTask(command);
            } else {
                next().execute(command);
            }
        }
    }

    interface ChannelFinder {
        AbstractAioChannel findChannel(Runnable command) throws Exception;
    }

    static class ReflectionChannelFinder implements ChannelFinder {
        private static final ConcurrentMap<Class<?>, Field[]> fieldCache = new ConcurrentHashMap<Class<?>, Field[]>();
        private static final Field[] FAILURE = new Field[0];

        @Override
        public AbstractAioChannel findChannel(Runnable command) throws Exception {
            Class<?> commandType = command.getClass();
            Field[] fields = fieldCache.get(commandType);
            if (fields == null) {
                try {
                    fields = findFieldSequence(command, new ArrayDeque<Field>(2));
                } catch (Throwable t) {
                    // Failed to get the field list
                }

                if (fields == null) {
                    fields = FAILURE;
                }

                fieldCache.put(commandType, fields); // No need to use putIfAbsent()
            }

            if (fields == FAILURE) {
                return null;
            }

            final int lastIndex = fields.length - 1;
            for (int i = 0; i < lastIndex; i ++) {
                command = (Runnable) get(fields[i], command);
            }

            return (AbstractAioChannel) get(fields[lastIndex], command);
        }

        private Field[] findFieldSequence(Runnable command, Deque<Field> fields) throws Exception {
            Class<?> commandType = command.getClass();
            for (Field f: commandType.getDeclaredFields()) {
                if (f.getType() == Runnable.class) {
                    f.setAccessible(true);
                    fields.addLast(f);
                    try {
                        Field[] ret = findFieldSequence((Runnable) get(f, command), fields);
                        if (ret != null) {
                            return ret;
                        }
                    } finally {
                        fields.removeLast();
                    }
                }

                if (f.getType() == Object.class) {
                    f.setAccessible(true);
                    fields.addLast(f);
                    try {
                        Object candidate = get(f, command);
                        if (candidate instanceof AbstractAioChannel) {
                            return fields.toArray(new Field[fields.size()]);
                        }
                    } finally {
                        fields.removeLast();
                    }
                }
            }

            return null;
        }

        protected Object get(Field f, Object command) throws Exception {
            return f.get(command);
        }
    }

    static class UnsafeChannelFinder extends ReflectionChannelFinder {
        private static final Unsafe UNSAFE = getUnsafe();

        @Override
        protected Object get(Field f, Object command) throws Exception {
            // using Unsafe to directly access the field. This should be
            // faster then "pure" reflection
            long offset = UNSAFE.objectFieldOffset(f);
            return UNSAFE.getObject(command, offset);
        }

        private static Unsafe getUnsafe() {
            try {
                Field singleoneInstanceField = Unsafe.class.getDeclaredField("theUnsafe");
                singleoneInstanceField.setAccessible(true);
                return (Unsafe) singleoneInstanceField.get(null);
            } catch (Throwable cause) {
                throw new RuntimeException("Error while obtaining sun.misc.Unsafe", cause);
            }
        }
    }
}
