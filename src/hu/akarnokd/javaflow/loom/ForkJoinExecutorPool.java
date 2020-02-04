package hu.akarnokd.javaflow.loom;

import java.util.concurrent.*;

public final class ForkJoinExecutorPool implements ExecutorPool, AutoCloseable {

    @Override
    public ExecutorWorker worker() {
        return new SingleExecutorWorker(ForkJoinPool.commonPool());
    }

    @Override
    public void close() {
        // the common pool can't be shut down
    }

    static final class SingleExecutorWorker implements ExecutorWorker {

        final ExecutorService service;

        SingleExecutorWorker(ExecutorService service) {
            this.service = service;
        }

        @Override
        public void execute(Runnable command) {
            service.execute(command);
        }

        @Override
        public void close() {
            // no op
        }
    }
}
