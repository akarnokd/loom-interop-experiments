package hu.akarnokd.javaflow.loom;

/**
 * A two-argument functional interface that can throw.
 * @param <T> the value type to be emitted via the emitter argument.
 */
@FunctionalInterface
public interface FiberGenerator<T> {

    void generate(Emitter<T> emitter) throws Throwable;
}
