package hu.akarnokd.javaflow.loom;

@FunctionalInterface
public interface FiberMapper<T, R> {

    void map(T input, Emitter<R> emitter) throws Throwable;
}
