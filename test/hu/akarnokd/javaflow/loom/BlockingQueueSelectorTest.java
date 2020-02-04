package hu.akarnokd.javaflow.loom;

import java.util.concurrent.*;

import org.junit.Test;

public class BlockingQueueSelectorTest {

    @Test(timeout = 100_000)
    public void normal() throws Exception {
        int n = 1_000_000;
        int m = 10;
        try (var scope = Executors.newUnboundedExecutor(Thread.builder().virtual(ForkJoinPool.commonPool()).factory())) {

            @SuppressWarnings("unchecked")
            BlockingQueue<Integer>[] queues = new BlockingQueue[m];

            for (int i = 0; i < queues.length; i++) {
                var q = new ArrayBlockingQueue<Integer>(10);
                queues[i] = q;

                scope.submit(() -> {
                    for (int j = 0; j < n; j++) {
                        q.put(j);
                    }
                    return null;
                });
            }

            try (var selector = new BlockingQueueSelector<>(queues, scope, 1)) {
                for (int i = 0; i < m * n; i++) {
                    selector.take();
                    if (i % 1000 == 0) {
                        System.out.println(i);
                    }
                }
            }
        }
    }
}
