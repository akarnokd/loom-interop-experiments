package hu.akarnokd.javaflow.loom;

public interface Emitter<T> {

    void emit(T item) throws Throwable;
}
