/*
 * Copyright 2022 The Netty Project
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
package io.netty5.buffer.internal;

import io.netty5.util.concurrent.FastThreadLocal;
import io.netty5.util.internal.SystemPropertyUtil;
import io.netty5.util.internal.ThreadExecutorMap;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.Cleaner;
import java.util.Optional;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * Cleaner allocation strategy used to assign cleaners to event-loop and external threads.
 * The default policy is used:
 * <ul>
 *     <li>for event-loop threads, by default a fast-thread-local shared-nothing Cleaner instance is allocated and used
 *         for each event-loop thread.</li>
 *     <li>for external threads, a shared Cleaner pool is used to distribute and map Cleaners to each external threads
 *     in a round-robin way using fast-thread-local. This pool is allocated lazily, just if some external threads need
 *     it</li>
 * </ul>
 *
 * <p>System property configuration:</p>
 * <ul>
 *     <li>-Dio.netty5.cleanerpool.size: integer value (default=1). Size of the shared Cleaner pool. This pool is
 *     used for external (non-event-loop) threads. "0" means all available processors are used as the pool size.</li>
 *     <li>-Dio.netty5.cleanerpool.eventloop.usepool: boolean (default=false). If set to true, all event-loop threads
 *     will use the shared threadpool, like external threads. This can be useful if we need to revert to a singleton
 *     Cleaner to be used by all event-loop/external threads (using io.netty5.cleanerpool.eventloop.usepool=true and
 *     io.netty5.cleanerpool.size=1). if set to false, it means all event-loop threads will be assigned to a dedicated
 *     Cleaner instance, so the pool won't be used in this case.</li>
 *     <li>-Dio.netty5.cleanerpool.vthread: boolean (default=false). If "true", and if the platform supports loom,
 *     then the Cleaners will be allocated using a ThreadFactory that returns virtual threads.</li>
 * </ul>
 */
final class CleanerPool {
    /**
     * Our logger.
     */
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(CleanerPool.class);

    /**
     * System property name used to configure the shared Cleaner pool size. External (non-event-loop) threads
     * will always use the shared cleaner pool. The shared cleaner pool is instantiated lazily (only if it is
     * needed).
     * default=1.
     * 0 means size will be set with the number of available processors.
     */
    private static final String CNF_POOL_SIZE = "io.netty5.cleanerpool.size";

    /**
     * System property name used to configure whether event-loop threads must use the shared cleaner pool.
     * default=false, meaning that each event-loop thread will use its own cleaner instance
     */
    private static final String CNF_EVENT_LOOP_USE_POOL = "io.netty5.cleanerpool.eventloop.usepool";

    /**
     * Size of the shared Cleaner pool used by external threads, and optionally by event-loop threads.
     * @see #CNF_POOL_SIZE
     */
    private static final int POOL_SIZE = getPoolSize(1);

    /**
     * Flag to configure whether event-loop threads must use the shared cleaner pool (default=false).
     * @see #CNF_EVENT_LOOP_USE_POOL
     */
    private static final boolean EVENT_LOOP_USE_POOL = SystemPropertyUtil.getBoolean(CNF_EVENT_LOOP_USE_POOL, false);

    /**
     * System property used to configure whether virtual threads should be used as cleaner daemon threads
     * (default=false)
     * if this property is set to true, and if the platform supports loom, then the Cleaners
     * will be created using virtual ThreadFactory obtained from Thread.ofVirtual().factory() method.
     */
    private static final String CNF_USE_VTHREAD = "io.netty5.cleanerpool.vthread";

    /**
     * If platform supports loom, then use virtual threads for cleaner daemon threads.
     * @see #CNF_USE_VTHREAD
     */
    private static final boolean USE_VTHREAD = SystemPropertyUtil.getBoolean(CNF_USE_VTHREAD, false);

    /**
     * Method handle used to optionally load Virtual ThreadFactory if the current platform is supporting Loom.
     */
    private static final MethodHandles.Lookup publicLookup = MethodHandles.publicLookup();

    /**
     * Method handle for Thread.ofVirtual() method. Null in case jvm version is < 19
     */
    private static final MethodHandle mhOfVirtual;

    /**
     * Method handle for java.lang.Thread.Builder.OfVirtual.factory() method. Null in case jvm version is < 19
     */
    private static final MethodHandle mhFactory;

    /**
     * Cleaner instances shared by all external threads, and optionally by event-loop threads
     * These cleaners are wrapped in a static inner class for lazy initialization purpose.
     */
    private static class CleanersPool {
        static final Cleaner[] cleaners = IntStream.range(0, POOL_SIZE)
                .mapToObj(i -> createCleaner()).toArray(Cleaner[]::new);
    }

    /**
     * A FastThreadLocal that returns a Cleaner to the caller thread.
     * For event-loop threads, by default a dedicated cleaner instance is allocated and mapped to the
     * caller thread, but by configuration, event-loop threads can use the shared cleaner pool if this is
     * necessary.
     * For external threads, a cleaner is always returned from the shared fixed cleaner pool in a round-robin way.
     */
    private final CleanerThreadLocal threadLocalCleaner = new CleanerThreadLocal();

    /**
     * Setup Method Handles that are used to obtain loom virtual ThreadFactory (if the platform supports it).
     */
    static {
        MethodHandle mhOfVirtualTmp = null;
        MethodHandle mhFactoryTmp = null;

        if (USE_VTHREAD) {
            try {
                Class<?> clzOfVirtual = Class.forName("java.lang.Thread$Builder$OfVirtual");
                mhOfVirtualTmp = publicLookup.findStatic(Thread.class, "ofVirtual",
                        MethodType.methodType(clzOfVirtual));
                mhFactoryTmp = publicLookup.findVirtual(clzOfVirtual, "factory",
                        MethodType.methodType(ThreadFactory.class));
            } catch (ClassNotFoundException e) {
                logger.debug("Loom not supported, Cleaner will use default cleaner daemon threads.");
            } catch (Throwable t) {
                logger.warn("Could not create virtual thread factory, will use default cleaner daemon threads.", t);
            }
        }

        mhOfVirtual = mhOfVirtualTmp;
        mhFactory = mhFactoryTmp;
    }

    /**
     * The singleton for the CleanerPool.
     */
    public static final CleanerPool INSTANCE = new CleanerPool();

    /**
     * The FastThreadLocal used to map a Cleaner instance to Event Loop threads.
     */
    private static class CleanerThreadLocal extends FastThreadLocal<Cleaner> {
        private static final AtomicInteger counter = new AtomicInteger();

        @Override
        protected Cleaner initialValue() {
            if (! EVENT_LOOP_USE_POOL && ThreadExecutorMap.currentExecutor() != null) {
                // Allocate one dedicated cleaner for the caller event-loop thread
                return createCleaner();
            } else {
                // Return one of the shared cleaners from the shared cleaner pool.
                return CleanersPool.cleaners[(counter.getAndIncrement() & 0x7F_FF_FF_FF) % POOL_SIZE];
            }
        }
    }

    private CleanerPool() {
        logger.debug("Instantiating CleanerPool: {}={}, {}={}, {}={}, using vthreads={}",
                CNF_EVENT_LOOP_USE_POOL, EVENT_LOOP_USE_POOL,
                CNF_POOL_SIZE, POOL_SIZE,
                CNF_USE_VTHREAD, USE_VTHREAD,
                mhFactory != null);
    }

    /**
     * Returns a Cleaner to the calling thread.
     * The same thread will get the same cleaner for every getCleaner method calls.
     */
    Cleaner getCleaner() {
        return threadLocalCleaner.get();
    }

    private static int getPoolSize(int defSize) {
        int poolSize = SystemPropertyUtil.getInt(CNF_POOL_SIZE, defSize);
        if (poolSize < 0) {
            throw new IllegalArgumentException(CNF_POOL_SIZE + " is negative: " + poolSize);
        }
        return poolSize == 0 ? Runtime.getRuntime().availableProcessors() : poolSize;
    }

    /**
     * Creates a cleaner initialized with a virtual ThreadFactory if the platform supports loom,
     * else creates a normal Cleaner with default cleaner daemon thread.
     */
    private static Cleaner createCleaner() {
        Optional<ThreadFactory> virtualThreadFactory = getVirtualThreadFactory();

        if (logger.isDebugEnabled()) {
            virtualThreadFactory.ifPresentOrElse(
                    threadFactory -> logger.debug("Creating cleaner with virtual thread factory"),
                    () -> logger.debug("Creating cleaner with default cleaner daemon thread"));
        }

        // If Loom is supported, return a cleaner based on virtual threads, else
        // return a normal cleaner that will use a default daemon thread.
        Optional<Cleaner> virtualCleaner = virtualThreadFactory.map(Cleaner::create);
        return virtualCleaner.orElse(Cleaner.create());
    }

    /**
     * Returns an optional Virtual Thread Factory in case Loom is supported.
     */
    private static Optional<ThreadFactory> getVirtualThreadFactory() {
        if (!USE_VTHREAD || mhOfVirtual == null) {
            return Optional.empty();
        }

        try {
            ThreadFactory threadFactory = (ThreadFactory) mhFactory.invoke(mhOfVirtual.invoke());
            return Optional.of(threadFactory);
        } catch (Throwable t) {
            logger.warn("Could not create virtual thread factory, will use default cleaner daemon threads.", t);
            return Optional.empty();
        }
    }
}
