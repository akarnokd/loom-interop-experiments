package hu.akarnokd.javaflow.loom;

import java.util.concurrent.*;

public final class ParallelExecutorPool implements ExecutorPool {

    final ExecutorService[] executors;

    int n;

    public ParallelExecutorPool(int parallelism) {
        this.executors = new ExecutorService[parallelism];
        for (int i = 0; i < parallelism; i++) {
            executors[i] = Executors.newSingleThreadExecutor();
        }
    }

    @Override
    public ExecutorWorker worker() {
        var index = n;

        var result = new SingleExecutorPool.SingleExecutorWorker(executors[index]);

        if (++index == executors.length) {
            index = 0;
        }
        // weak round-robin
        n = index;
        return result;
    }

    @Override
    public void close() {
        for (var executor : executors) {
            executor.shutdown();
        }
    }
}
