package hu.akarnokd.javaflow.loom;

import java.util.concurrent.atomic.AtomicReference;

public class Resumable extends AtomicReference<Continuation> {

    private static final long serialVersionUID = -4732819140145898673L;

    
    public void await() {
    }
    
    public void resume() {
    }
    
    static final ContinuationScope SCOPE = new ContinuationScope("Resumable.Global");
    static final Continuation READY = new Continuation(SCOPE, () -> {});
}
