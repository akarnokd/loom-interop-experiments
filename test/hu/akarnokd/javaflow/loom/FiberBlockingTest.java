package hu.akarnokd.javaflow.loom;

import java.util.concurrent.*;

import org.junit.Test;

public class FiberBlockingTest {

    @Test
    public void doesFiberBlockAnExecutor() throws Exception {
var exec = Executors.newSingleThreadExecutor();
try {
    Future<?> f = exec.submit(() -> {
        try (var scope = FiberScope.open()) {
            System.out.println("In scope");
            Thread.sleep(1000);
        }
        System.out.println("Out of scope");
        return null;
    });
    
    Future<?> f2 = exec.submit(() -> {
        System.out.println("Another task");
        Thread.sleep(250);
        System.out.println("Back");
        return null;
    });
    
    f.get();
    f2.get();
} finally {
    exec.shutdown();
}
    }
    

    @Test
    public void doesFiberBlockAnExecutorInsideOut() throws Exception {
        var exec = Executors.newSingleThreadExecutor();
        try {
            try (var scope = FiberScope.open()) {
                scope.schedule(exec, () -> {
                    System.out.println("F1 start");
                    Thread.sleep(1000);
                    System.out.println("F1 end");
                    return null;
                });
                
                scope.schedule(exec, () -> {
                    Thread.sleep(250);
                    System.out.println("Something else");
                    return null;
                });
            }
        } finally {
            exec.shutdown();
            exec.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
