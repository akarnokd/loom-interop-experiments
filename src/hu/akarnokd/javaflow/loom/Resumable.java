package hu.akarnokd.javaflow.loom;

import java.util.concurrent.atomic.AtomicBoolean;

public class Resumable extends AtomicBoolean {

    private static final long serialVersionUID = -4732819140145898673L;

    final ContinuationScope scope;

    public Resumable() {
        this.scope = new ContinuationScope("Resumable");
    }

    public final void await() {
        if (!get()) {
            Continuation.yield(scope);
        }
        set(false);
    }

    public final void resume() {
        if (!get() && compareAndSet(false, true)) {
            Continuation.getCurrentContinuation(scope).run();
        }
    }
}
