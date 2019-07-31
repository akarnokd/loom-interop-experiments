package hu.akarnokd.javaflow.loom;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

public class ResumableFiber extends AtomicReference<Object> {

    private static final long serialVersionUID = -3462467580179834124L;
    
    static final Object READY = new Object();

    public void await() {
        Object toUnpark;
        var fiber = Fiber.current();
        if (fiber.isEmpty()) {
            toUnpark = Thread.currentThread();
        } else {
            toUnpark = fiber.get();
        }

        for (;;) {
            var current = get();
            if (current == READY) {
                break;
            }

            if (current != null && current != toUnpark) {
                throw new IllegalStateException("Only one Thread/Fiber can await this ResumableFiber!");
            }

            if (compareAndSet(null, toUnpark)) {
                LockSupport.park();
                // we don't just break here because park() can wake up spuriously
                // if we got a proper resume, get() == READY and the loop will quit above
            }
        }
        getAndSet(null);
    }
    
    public void resume() {
        if (get() != READY) {
            var old = getAndSet(READY);
            if(old != READY) {
                LockSupport.unpark(old);
            }
        }
    }
}
