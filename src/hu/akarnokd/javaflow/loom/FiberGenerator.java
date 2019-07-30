package hu.akarnokd.javaflow.loom;

import java.util.function.Consumer;

/**
 * A two-argument functional interface that can throw.
 * @param <T> the value type to be emitted via the emitter argument.
 */
@FunctionalInterface
public interface FiberGenerator<T> {

    void generate(FiberScope scope, Consumer<T> emitter) throws Throwable;
}
