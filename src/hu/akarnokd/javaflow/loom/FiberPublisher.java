package hu.akarnokd.javaflow.loom;

import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.*;
import java.util.function.*;

public final class FiberPublisher<T> implements Flow.Publisher<T> {

    final FiberGenerator<T> generator;
    
    final FiberScope.Option[] options;

    public FiberPublisher(FiberGenerator<T> generator, FiberScope.Option... options) {
        this.generator = generator;
        this.options = options;
    }



    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        var fs = new FiberSubscription<T>(subscriber);
        fs.run(generator, options);
    }

    static final class FiberSubscription<T> extends AtomicLong implements Flow.Subscription, Consumer<T> {

        private static final long serialVersionUID = -7057285967151832152L;

        final Subscriber<? super T> downstream;

        volatile RuntimeException stop;
        static final RuntimeException STOP = new RuntimeException("Cancellation from downstream");
        
        final ReentrantLock lock;
        
        final Condition condition;
        
        FiberSubscription(Subscriber<? super T> downstream) {
            this.downstream = downstream;
            this.lock = new ReentrantLock();
            this.condition = lock.newCondition();
        }

        void run(FiberGenerator<T> generator, FiberScope.Option... options) {
            try {
                try (FiberScope scope = FiberScope.open(options)) {
                    downstream.onSubscribe(this);
                    generator.generate(scope, this);
                }
            } catch (Throwable ex) {
                if (ex != STOP) {
                    downstream.onError(ex);
                }
                return;
            }
            if (stop == null) {
                downstream.onComplete();
            }
        }
        
        @Override
        public void accept(T t) {
            if (get() == 0 && stop == null) {
                await();
            }
            var s = stop;
            if (s == null) {
                downstream.onNext(t);
                
                decrementAndGet();
            } else {
                throw s;
            }
        }

        @Override
        public void request(long n) {
            if(n <= 0) {
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

        void await() {
            lock.lock();
            try {
                while (get() == 0) {
                    condition.await();
                }
            } catch (InterruptedException ex) {
                stop = new RuntimeException(ex);
            } finally {
                lock.unlock();
            }
        }
        
        void resume() {
            lock.lock();
            try {
                condition.signalAll();
            } finally {
                lock.unlock();
            }
        }
        
        @Override
        public void cancel() {
            stop = STOP;
            request(1);
        }
        
    }
}
