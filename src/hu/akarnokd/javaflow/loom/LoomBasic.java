package hu.akarnokd.javaflow.loom;

public class LoomBasic {

    public static void main(String[] args) {

        var scope  = new ContinuationScope("main");

        var cont = new Continuation(scope, () -> {
            for (int i = 0; i < 10; i++) {
                System.out.println("Hello");
                Continuation.yield(scope);
            }
            System.out.println("Done continuation");
        });

        while (!cont.isDone()) {
            cont.run();
        }
        System.out.println("Resumer done");
        
        try (var fscope = FiberScope.open()) {
        }
    }
}
