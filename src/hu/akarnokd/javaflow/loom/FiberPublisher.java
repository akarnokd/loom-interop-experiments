package hu.akarnokd.javaflow.loom;

import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.*;

public final class FiberPublisher<T> implements Flow.Publisher<T> {

    final FiberGenerator<T> generator;

    public FiberPublisher(FiberGenerator<T> generator) {
        this.generator = generator;
    }

    @Override
    public void subscribe(Subscriber<? super T> downstream) {
        var fs = new FiberSubscription<T>(downstream);
        try {
            downstream.onSubscribe(fs);
            generator.generate(fs);
        } catch (Throwable ex) {
            if (ex != STOP) {
                downstream.onError(ex);
            }
            return;
        }
        if (fs.stop == null) {
            downstream.onComplete();
        }
    }

    static final RuntimeException STOP = new RuntimeException("Cancellation from downstream");

    static final class FiberSubscription<T> extends AtomicLong implements Flow.Subscription, Emitter<T> {

        private static final long serialVersionUID = -7057285967151832152L;

        final Subscriber<? super T> downstream;

        volatile RuntimeException stop;

        final ReentrantLock lock;

        final Condition condition;

        long produced;

        FiberSubscription(Subscriber<? super T> downstream) {
            this.downstream = downstream;
            this.lock = new ReentrantLock();
            this.condition = lock.newCondition();
        }

        @Override
        public void emit(T t) throws Throwable {
            var p = produced;
            if (get() == p && stop == null) {
                await(p);
            }
            var s = stop;
            if (s == null) {
                downstream.onNext(t);

                produced = p + 1;
            } else {
                throw s;
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
                    break;
                }

                var next = current + n;
                if (next < 0L) {
                    next = Long.MAX_VALUE;
                }

                if (compareAndSet(current, next)) {
                    resume();
                    break;
                }
            }
        }

        void await(long p) throws InterruptedException {
            lock.lock();
            try {
                while (get() == p) {
                    condition.await();
                }
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
