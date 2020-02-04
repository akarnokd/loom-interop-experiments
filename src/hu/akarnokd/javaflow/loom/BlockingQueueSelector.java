package hu.akarnokd.javaflow.loom;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BlockingQueueSelector<T> implements AutoCloseable {

    final BlockingQueue<T>[] queues;

    final ExecutorService executor;

    final BlockingQueue<T> outputQueue;

    final AtomicBoolean once;

    final Future<?>[] futures;

    public BlockingQueueSelector(BlockingQueue<T>[] queues, ExecutorService executor, int capacity) {
        this.queues = queues;
        this.executor = executor;
        this.outputQueue = capacity != Integer.MAX_VALUE ? new ArrayBlockingQueue<>(capacity) : new LinkedBlockingQueue<>();
        this.once = new AtomicBoolean();
        this.futures = new Future[queues.length];
    }

    public T take() throws InterruptedException {
        if (!once.get() && once.compareAndSet(false, true)) {
            int i = 0;
            for (BlockingQueue<T> queue : queues) {
                futures[i] = executor.submit(() -> {
                    while (!Thread.currentThread().isInterrupted()) {
                        outputQueue.put(queue.take());
                    }
                    return null;
                });
                i++;
            }
        }
        return outputQueue.take();
    }

    @Override
    public void close() throws Exception {
        for (Future<?> future : futures) {
            if (future != null) {
                future.cancel(true);
            }
        }
    }
}
