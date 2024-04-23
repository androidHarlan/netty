/*
 * Copyright 2024 The Netty Project
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
package io.netty.channel;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.RejectedExecutionHandler;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.SystemPropertyUtil;

import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

/**
 * {@link IoEventLoop} implementation that execute all its submitted tasks in a single thread using the provided
 * {@link IoHandler}.
 */
public class SingleThreadIoEventLoop extends SingleThreadEventLoop implements IoEventLoop {

    // TODO: Is this a sensible default ?
    protected static final int DEFAULT_MAX_TASKS_PER_RUN = Math.max(1,
            SystemPropertyUtil.getInt("io.netty.eventLoop.maxTaskPerRun", 1024 * 4));

    private final int maxTasksPerRun = DEFAULT_MAX_TASKS_PER_RUN;
    private final IoExecutionContext context = new IoExecutionContext() {
        @Override
        public boolean canBlock() {
            assert inEventLoop();
            return !hasTasks() && !hasScheduledTasks();
        }

        @Override
        public long delayNanos(long currentTimeNanos) {
            assert inEventLoop();
            return SingleThreadIoEventLoop.this.delayNanos(currentTimeNanos);
        }

        @Override
        public long deadlineNanos() {
            assert inEventLoop();
            return SingleThreadIoEventLoop.this.deadlineNanos();
        }
    };

    private final IoHandler ioHandler;

    /**
     *  Creates a new instance
     *
     * @param parent            the parent that holds this {@link IoEventLoop}.
     * @param ioHandler         the {@link IoHandler} used to run all IO.
     * @param threadFactory     the {@link ThreadFactory} that is used to create the underlying {@link Thread}.
     */
    public SingleThreadIoEventLoop(IoEventLoopGroup parent, ThreadFactory threadFactory,
                                   IoHandler ioHandler) {
        super(parent, threadFactory, false);
        this.ioHandler = ObjectUtil.checkNotNull(ioHandler, "ioHandler");
    }

    /**
     *  Creates a new instance
     *
     * @param parent            the parent that holds this {@link IoEventLoop}.
     * @param executor          the {@link Executor} that is used for dispatching the work.
     * @param ioHandler         the {@link IoHandler} used to run all IO.
     */
    public SingleThreadIoEventLoop(IoEventLoopGroup parent, Executor executor, IoHandler ioHandler) {
        super(parent, executor, false);
        this.ioHandler = ObjectUtil.checkNotNull(ioHandler, "ioHandler");
    }

    /**
     *  Creates a new instance
     *
     * @param parent                    the parent that holds this {@link IoEventLoop}.
     * @param threadFactory             the {@link ThreadFactory} that is used to create the underlying {@link Thread}.
     * @param ioHandler                 the {@link IoHandler} used to run all IO.
     * @param maxPendingTasks           the maximum pending tasks that are allowed before
     *                                  {@link RejectedExecutionHandler#rejected(Runnable, SingleThreadEventExecutor)}
     *                                  is called to handle it.
     * @param rejectedExecutionHandler  the {@link RejectedExecutionHandler} that handles when more tasks are added
     *                                  then allowed per {@code maxPendingTasks}.
     */
    public SingleThreadIoEventLoop(IoEventLoopGroup parent, ThreadFactory threadFactory,
                                   IoHandler ioHandler, int maxPendingTasks,
                                   RejectedExecutionHandler rejectedExecutionHandler) {
        super(parent, threadFactory, false, maxPendingTasks, rejectedExecutionHandler);
        this.ioHandler = ObjectUtil.checkNotNull(ioHandler, "ioHandler");
    }

    /**
     *  Creates a new instance
     *
     * @param parent                    the parent that holds this {@link IoEventLoop}.
     * @param ioHandler                 the {@link IoHandler} used to run all IO.
     * @param executor                  the {@link Executor} that is used for dispatching the work.
     * @param maxPendingTasks           the maximum pending tasks that are allowed before
     *                                  {@link RejectedExecutionHandler#rejected(Runnable, SingleThreadEventExecutor)}
     *                                  is called to handle it.
     * @param rejectedExecutionHandler  the {@link RejectedExecutionHandler} that handles when more tasks are added
     *                                  then allowed per {@code maxPendingTasks}.
     */
    public SingleThreadIoEventLoop(IoEventLoopGroup parent, Executor executor,
                                   IoHandler ioHandler, int maxPendingTasks,
                                   RejectedExecutionHandler rejectedExecutionHandler) {
        super(parent, executor, false, maxPendingTasks, rejectedExecutionHandler);
        this.ioHandler = ObjectUtil.checkNotNull(ioHandler, "ioHandler");
    }

    /**
     *
     *  Creates a new instance
     *
     * @param parent                    the parent that holds this {@link IoEventLoop}.
     * @param executor                  the {@link Executor} that is used for dispatching the work.
     * @param ioHandler                 the {@link IoHandler} used to run all IO.
     * @param taskQueue                 the {@link Queue} used for storing pending tasks.
     * @param tailTaskQueue             the {@link Queue} used for storing tail pending tasks.
     * @param rejectedExecutionHandler  the {@link RejectedExecutionHandler} that handles when more tasks are added
     *                                  then allowed.
     */
    protected SingleThreadIoEventLoop(IoEventLoopGroup parent, Executor executor,
                                      IoHandler ioHandler, Queue<Runnable> taskQueue,
                                      Queue<Runnable> tailTaskQueue,

                                      RejectedExecutionHandler rejectedExecutionHandler) {
        super(parent, executor, false, taskQueue, tailTaskQueue, rejectedExecutionHandler);
        this.ioHandler = ObjectUtil.checkNotNull(ioHandler, "ioHandler");
    }

    @Override
    protected void run() {
        assert inEventLoop();
        do {
            runIo();
            if (isShuttingDown()) {
                ioHandler.prepareToDestroy();
            }
            runAllTasks(maxTasksPerRun);
        } while (!confirmShutdown());
    }

    protected final IoHandler ioHandler() {
        return ioHandler;
    }

    /**
     * Called when IO will be processed for all the {@link IoHandle}s on this {@link SingleThreadIoEventLoop}.
     * This method returns the number of {@link IoHandle}s for which IO was processed.
     *
     * This method must be called from the {@link EventLoop} thread.
     */
    protected int runIo() {
        assert inEventLoop();
        return ioHandler.run(context);
    }

    @Override
    public IoEventLoop next() {
        return this;
    }

    @Override
    public final Future<Void> registerForIo(final IoHandle handle) {
        final Promise<Void> promise = newPromise();
        if (inEventLoop()) {
            registerForIo0(handle, promise);
        } else {
            execute(new Runnable() {
                @Override
                public void run() {
                    registerForIo0(handle, promise);
                }
            });
        }
        return promise;
    }

    private void registerForIo0(IoHandle handle, Promise<Void> promise) {
        assert inEventLoop();
        try {
            if (handle.isRegistered()) {
                throw new IllegalStateException("IoHandle already registered");
            }

            checkInEventLoopIfPossible(handle);

            ioHandler.register(handle);
        } catch (Throwable cause) {
            promise.setFailure(cause);
            return;
        }
        promise.setSuccess(null);
    }

    @Override
    public final Future<Void> deregisterForIo(final IoHandle handle) {
        final Promise<Void> promise = newPromise();
        if (inEventLoop()) {
            deregisterForIo0(handle, promise);
        } else {
            execute(new Runnable() {
                @Override
                public void run() {
                    deregisterForIo0(handle, promise);
                }
            });
        }
        return promise;
    }

    private void deregisterForIo0(IoHandle handle, Promise<Void> promise) {
        assert inEventLoop();
        try {
            if (!handle.isRegistered()) {
                throw new IllegalStateException("Channel not registered");
            }
            checkInEventLoopIfPossible(handle);
            ioHandler.deregister(handle);
        } catch (Throwable cause) {
            promise.setFailure(cause);
            return;
        }
        promise.setSuccess(null);
    }

    private static void checkInEventLoopIfPossible(IoHandle handle) {
        if (handle instanceof Channel && !((Channel) handle).eventLoop().inEventLoop()) {
            throw new IllegalStateException("Channel.eventLoop() is not using the same Thread as this EventLoop");
        }
    }

    @Override
    protected final void wakeup(boolean inEventLoop) {
        ioHandler.wakeup(inEventLoop);
    }

    @Override
    protected final void cleanup() {
        assert inEventLoop();
        ioHandler.destroy();
    }

    @Override
    public boolean isCompatible(Class<? extends IoHandle> handleType) {
        return ioHandler.isCompatible(handleType);
    }
}
