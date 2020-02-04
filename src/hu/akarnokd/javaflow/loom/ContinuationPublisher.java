package hu.akarnokd.javaflow.loom;

import java.util.concurrent.*;
import java.util.concurrent.Flow.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class ContinuationPublisher<T> implements Flow.Publisher<T> {

    final Consumer<Consumer<? super T>> continuableGenerator;

    public ContinuationPublisher(Consumer<Consumer<? super T>> continuableGenerator) {
        this.continuableGenerator = continuableGenerator;
    }
    
    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        var cs = new ContinuationSubscription<>(subscriber, continuableGenerator);
        cs.continuation = new Continuation(cs.scope, cs);
        subscriber.onSubscribe(cs);
    }
    
    static final class ContinuationSubscription<T> extends AtomicLong implements Subscription, Consumer<T>, Runnable {

        private static final long serialVersionUID = 6232067150858919051L;

        final Subscriber<? super T> downstream;
        
        final ContinuationScope scope = new ContinuationScope("ContinuationSubscription");
        
        final Consumer<Consumer<? super T>> continuableGenerator;
        
        static final RuntimeException STOP = new RuntimeException("Cancellation from downstream");
        
        Continuation continuation;
        
        volatile RuntimeException stop;
        
        ContinuationSubscription(Subscriber<? super T> downstream, Consumer<Consumer<? super T>> continuableGenerator) {
            this.downstream = downstream;
            this.continuableGenerator = continuableGenerator;
        }
        
        @Override
        public void accept(T t) {
            if (get() == 0L && stop == null) {
                Continuation.yield(scope);
            }
            var stop = this.stop;
            if (stop == null) {
                downstream.onNext(t);
                
                addAndGet(-1);
            } else {
                throw stop;
            }
        }

        @Override
        public void request(long n) {
            if (n <= 0) {
                stop = new IllegalArgumentException("§3.9 violated: positive request amount required but it was " + n);
                n = 1; // this will resume a suspended continuation
            }
            for (;;) {
                var current = get();
                if (current == Long.MAX_VALUE) {
                    return;
                }
                
                var next = current + n;
                if (next < 0L) {
                    next = Long.MAX_VALUE;
                }
                
                if (compareAndSet(current, next)) {
                    if (current == 0L) {
                        resume();
                    }
                    return;
                }
            }
        }

        void resume() {
            while (!continuation.isDone()) {
                try {
                    continuation.run();
                    return;
                } catch (IllegalStateException ignored) {
                    // could mean the continuation has ended and there is nothing to resume
                }
            }
        }
        
        @Override
        public void cancel() {
            stop = STOP;
            request(1); // this should unsuspend accept if yielding
        }
     
        @Override
        public void run() {
            try {
                continuableGenerator.accept(this);
            } catch (RuntimeException ex) {
                if (ex != STOP) {
                    downstream.onError(ex);
                }
                return;
            }
            if (stop == null) {
                downstream.onComplete();
            }
        }
    }
}
