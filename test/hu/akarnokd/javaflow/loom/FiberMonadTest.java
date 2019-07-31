package hu.akarnokd.javaflow.loom;

import org.junit.Test;
import static org.junit.Assert.*;

public class FiberMonadTest {

    @Test
    public void basic() throws Exception {
        try (var pool = new SingleExecutorPool()) {
            try (var scope = FiberScope.open()) {
                
                var n = 100;
                
                var source = new FiberPublisher<Integer>((sc, emitter) -> {
                    for (var i = 0; i < n; i++) {
                        emitter.accept(i);
                    }
                });
                
                var asyncSource = new FiberSubscribeOnPublisher<>(source, pool);
                
                var mapped = new FiberMap<Integer, Integer>(asyncSource, (value, emitter) -> {
                    emitter.emit(value);
                    emitter.emit(value + 1);
                }, pool, 1);
                
                var consumer = new FiberConsumer<>(mapped);
                
                int i = 0;
                try (var iterator = consumer.iterator()) {
                    while (iterator.hasNext()) {
                        assertEquals(i, iterator.next().intValue());
                        assertTrue(iterator.hasNext());
                        assertEquals(i + 1, iterator.next().intValue());
                        i++;
                    }
                }
                
                assertEquals(n, i);
            }
        }
    }
}
