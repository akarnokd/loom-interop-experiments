package hu.akarnokd.javaflow.loom;

public class LoomFiber {

    public static void main(String[] args) throws Exception {
        try (var fiber = FiberScope.open()) {
            
            var f1 = fiber.schedule(() -> 1);
            var f2 = fiber.schedule(() -> 1);
            
            System.out.println(f1.join() + f2.join());
        }
    }
}
