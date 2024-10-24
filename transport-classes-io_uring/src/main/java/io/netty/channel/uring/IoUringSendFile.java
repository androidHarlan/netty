package io.netty.channel.uring;

import io.netty.channel.EventLoop;
import io.netty.channel.IoEvent;
import io.netty.channel.IoEventLoop;
import io.netty.channel.IoRegistration;
import io.netty.channel.unix.Errors;
import io.netty.channel.unix.FileDescriptor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class IoUringSendFile implements IoUringIoHandle {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(IoUringSendFile.class);

    private final Supplier<PipeFd> pipeSupplier;

    private final IoEventLoop eventLoop;

    private long spliceOperationId;

    private Promise<Integer> spliceResult;

    private Stage stage;

    private PipeFd currentPipe;

    private IoUringIoRegistration ioRegistration;

    private long outOffset;

    private int len;

    private FileDescriptor outFd;

    private int spliceFlags;

    private AtomicBoolean closed = new AtomicBoolean(false);

    private IoUringSendFile(Supplier<PipeFd> pipeSupplier, IoEventLoop eventLoop) {
        this.pipeSupplier = pipeSupplier;
        this.eventLoop = eventLoop;
        this.stage = Stage.IDLE;
    }

    public static Future<IoUringSendFile> newInstance(Supplier<PipeFd> pipeSupplier, EventLoop eventLoop) {
        //we may need `Flexible Constructor Bodies` to check something
        //but in jdk8, we only use factory method to create a new instance
        if (!IoUring.isIOUringSpliceSupported()) {
            throw new UnsupportedOperationException("io_uring splice is not supported");
        }

        boolean isIoEventLoop = eventLoop instanceof IoEventLoop;
        if (!isIoEventLoop || !((IoEventLoop) eventLoop).isCompatible(IoUringSendFile.class)) {
            throw new IllegalArgumentException("incompatible event loop type: " + eventLoop.getClass().getName());
        }

        IoUringSendFile unRegisteredInstance = new IoUringSendFile(pipeSupplier, (IoEventLoop) eventLoop);
        Promise<IoUringSendFile> registerPromise = eventLoop.newPromise();
        // Use anonymous classes to avoid the warm-up issue of the initial access of lambdas.
        ((IoEventLoop) eventLoop).register(unRegisteredInstance)
                .addListener(new GenericFutureListener<Future<? super IoRegistration>>() {
                    @Override
                    public void operationComplete(Future<? super IoRegistration> future) throws Exception {
                        if (future.isSuccess()) {
                            unRegisteredInstance.ioRegistration = (IoUringIoRegistration) future.getNow();
                            registerPromise.setSuccess(unRegisteredInstance);
                        } else {
                            registerPromise.setFailure(future.cause());
                        }
                    }
                });
        return registerPromise;
    }


    @Override
    public void handle(IoRegistration registration, IoEvent ioEvent) {
        IoUringIoEvent uringIoEvent = (IoUringIoEvent) ioEvent;
        int res = uringIoEvent.res();
        if (res < 0) {
            spliceResult.setFailure(new Errors.NativeIoException("IoUringSplicer", -res));
            clear();
            return;
        }

        switch (stage) {
            case SPLICE_TO_PIPE: {
                spliceFromPipe(outFd, outOffset, len, spliceFlags);
                return;
            }
            case SPLICE_FROM_PIPE: {
                spliceResult.setSuccess(res);
                clear();
                return;
            }
        }
    }

    public Future<Integer> sendFile(FileDescriptor outFd, long outOffset, FileDescriptor inFd, long inOffset, int len, int spliceFlags) {

        if (closed.get()) {
            return eventLoop.newFailedFuture(new IllegalStateException("closed"));
        }

        Promise<Integer> promise = eventLoop.newPromise();
        if (eventLoop.inEventLoop()) {
            sendFile0(outFd, outOffset, inFd, inOffset, len, spliceFlags, promise);
        } else {
            eventLoop.execute(new Runnable() {
                @Override
                public void run() {
                    sendFile0(outFd, outOffset, inFd, inOffset, len, spliceFlags, promise);
                }
            });
        }
        return promise;
    }

    private void sendFile0(FileDescriptor outFd, long outOffset, FileDescriptor inFd, long inOffset, int len, int spliceFlags, Promise<Integer> promise) {
        assert eventLoop.inEventLoop();

        if (!ioRegistration.isValid()) {
            promise.setFailure(new IllegalStateException("invalid"));
            return;
        }

        if (closed.get()) {
            promise.setFailure(new IllegalStateException("closed"));
            return;
        }

        if (stage != Stage.IDLE) {
            promise.setFailure(new IllegalStateException("sendfile task is running"));
            return;
        }

        try {
            currentPipe = pipeSupplier.get();
            spliceToPipe(inFd, inOffset, len, spliceFlags);
        } catch (Exception e) {
            promise.setFailure(e);
        }
        this.spliceResult = promise;
        this.outOffset = outOffset;
        this.len = len;
        this.outFd = outFd;
        this.spliceFlags = spliceFlags;
    }


    private void spliceToPipe(FileDescriptor inFd, long offset, int len, int spliceFlags) {
        assert eventLoop.inEventLoop();
        assert currentPipe != null;
        assert stage == Stage.IDLE;

        stage = Stage.SPLICE_TO_PIPE;
        IoUringIoOps ioUringIoOps = IoUringIoOps.newSplice(
                inFd.intValue(), offset,
                currentPipe.writeFd().intValue(), -1L,
                len, spliceFlags
        );
        spliceOperationId = ioRegistration.submit(ioUringIoOps);
    }

    private void spliceFromPipe(FileDescriptor outFd, long offset, int len, int spliceFlags) {
        assert eventLoop.inEventLoop();
        assert currentPipe != null;
        assert stage == Stage.SPLICE_TO_PIPE;

        stage = Stage.SPLICE_FROM_PIPE;
        IoUringIoOps ioUringIoOps = IoUringIoOps.newSplice(
                currentPipe.readFd().intValue(), -1L,
                outFd.intValue(), offset,
                len, spliceFlags
        );
        spliceOperationId = ioRegistration.submit(ioUringIoOps);
    }

    private void clear() {
        spliceResult = null;
        stage = Stage.IDLE;
        outOffset = 0;
        len = 0;
        outFd = null;
        spliceFlags = 0;
        try {
            currentPipe.close();
        } catch (Exception e) {
            logger.error("Error while closing a pipe", e);
        }
        currentPipe = null;

    }

    @Override
    public void close() throws Exception {

        ioRegistration.cancel();
        clear();
    }

    enum Stage {
        IDLE,
        SPLICE_TO_PIPE,
        SPLICE_FROM_PIPE
    }

}
