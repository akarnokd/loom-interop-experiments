package hu.akarnokd.javaflow.loom;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import static org.junit.Assert.*;

public class ResumableFiberTest {

    @Test
    public void pingPong() throws Exception {
        try (var scope = FiberScope.open()) {
            
            var producerReady = new ResumableFiber();
            var consumerReady = new ResumableFiber();
            
            var n = 100_000;
            
            var exchange = new AtomicReference<Integer>();

            var job1 = scope.schedule(() -> {
                Fiber.current().get(); // We should be in a Fiber
                for (int i = 1; i <= n; i++) {
                    
                    consumerReady.await();
                    
                    assertNull(exchange.get());
                    
                    exchange.lazySet(i);
                    
                    producerReady.resume();
                }
                return null;
            });
            
            var job2 = scope.schedule(() -> {
                Fiber.current().get(); // We should be in a Fiber
                var value = -1;
                while (value != n) {
                    consumerReady.resume();
                    
                    producerReady.await();
                    
                    value = exchange.get();
                    exchange.lazySet(null);
                }
                return value;
            });
            
            job1.join();
            
            assertEquals(n, job2.join().intValue());
        }
    }
}
