package hu.akarnokd.javaflow.loom;

import java.util.concurrent.*;

public final class SingleExecutorPool implements ExecutorPool, AutoCloseable {

    final ExecutorService service = Executors.newSingleThreadExecutor();

    @Override
    public ExecutorWorker worker() {
        return new SingleExecutorWorker(this.service);
    }

    @Override
    public void close() {
        service.shutdown();
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
