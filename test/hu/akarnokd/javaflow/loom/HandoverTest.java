package hu.akarnokd.javaflow.loom;

import java.util.concurrent.*;

import org.junit.*;

public class HandoverTest {

    @Test(timeout = 5000)
    @Ignore("Doesn't work, one needs to call get()/emit() in the right scope")
    public void basic() throws Exception {
        var h = new Handover<Integer>();
        var n = 1_000_000;

        ForkJoinTask<?> t = ForkJoinPool.commonPool().submit(() -> {
            while (h.get().intValue() != n) { }
        });

        for (int i = 1; i <= n; i++) {
            h.emit(i);
        }

        t.join();
    }
}
