package hu.akarnokd.javaflow.loom;

import static org.junit.Assert.assertEquals;

import java.util.*;
import java.util.concurrent.SubmissionPublisher;

import org.junit.Test;

public class FiberConsumerTest {

    @Test
    public void basic() throws Exception {
        List<Integer> values = new ArrayList<>();
        
        var sp = new SubmissionPublisher<Integer>();
        try (FiberScope scope = FiberScope.open()) {
            
            var consumer = new FiberConsumer<>(sp);
            
            try (var iter = consumer.iterator()) {

                scope.schedule(() -> {
                    for (int i = 1; i < 11; i++) {
                        System.out.println("Emitting " + i);
                        sp.submit(i);
                    }
                    System.out.println("Source done");
                    sp.close();
                });
                
                System.out.println("Waiting for first");
                while (iter.hasNext()) {
                    var i = iter.next();
                    System.out.println("Receiving " + i);
                    values.add(i);
                    System.out.println("Waiting for next");
                }
                System.out.println("Iterator done.");
            }
        }
        
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), values);
    }
}
