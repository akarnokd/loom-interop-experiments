package hu.akarnokd.javaflow.loom;

import java.util.concurrent.Executor;

public interface ExecutorWorker extends Executor, AutoCloseable {

    @Override
    void close();
}
