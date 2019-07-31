package hu.akarnokd.javaflow.loom;

import java.util.concurrent.*;

import org.junit.*;

public class FiberBlockingTest {

    @Test
    public void doesFiberBlockAnExecutor() throws Exception {
        var exec = Executors.newSingleThreadExecutor();
        try {
            var f = exec.submit(() -> {
                try (var scope = FiberScope.open()) {
                    System.out.println("In scope");
                    Thread.sleep(1000);
                }
                System.out.println("Out of scope");
                return null;
            });
            
            var f2 = exec.submit(() -> {
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
    public void doesFiberBlockAnExecutorInnerSchedule() throws Exception {
        var exec = Executors.newSingleThreadExecutor();
        try {
            var f = exec.submit(() -> {
                try (var scope = FiberScope.open()) {
                    scope.schedule(() -> {
                        System.out.println("In scope");
                        Thread.sleep(1000);
                        System.out.println("Out of scope");
                        return null;
                    }).join();
                    System.out.println("End");
                }
                return null;
            });
            
            var f2 = exec.submit(() -> {
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
    @Ignore("Doesn't work, close() throws IAE and this still blcoks the executor.")
    public void doesFiberBlockAnExecutorBackground() throws Exception {
        var exec = Executors.newSingleThreadExecutor();
        try {
            var f = exec.submit(() -> {
                try (var scope = FiberScope.background()) {
                    System.out.println("In scope");
                    Thread.sleep(1000);
                    System.out.println("End of scope");
                }
                return null;
            });
            
            var f2 = exec.submit(() -> {
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

    @Test
    public void doesFiberBlockAnExecutorLazyScope() throws Exception {
        var exec = Executors.newSingleThreadExecutor();
        try {
            
            var f1 = FiberScope.background().schedule(exec, () -> {
                System.out.println("In scope");
                Thread.sleep(1000);
                System.out.println("Out of scope");
                return null;
            });
            
            var f2 = FiberScope.background().schedule(exec, () -> {
                System.out.println("Another task");
                Thread.sleep(250);
                System.out.println("Back");
                return null;
            });
            
            f1.join();
            f2.join();
        } finally {
            exec.shutdown();
        }
    }
}
