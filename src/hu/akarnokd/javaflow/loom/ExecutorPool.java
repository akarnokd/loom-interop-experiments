package hu.akarnokd.javaflow.loom;

public interface ExecutorPool extends AutoCloseable {

    ExecutorWorker worker();
    
    @Override
    void close();
}
