package hu.akarnokd.javaflow.loom;

import java.util.concurrent.*;
import java.util.concurrent.Flow.*;
import java.util.concurrent.atomic.AtomicReference;

public final class FiberSubscribeOnPublisher<T> implements Flow.Publisher<T> {

    final Flow.Publisher<? extends T> source;
    
    final ExecutorPool pool;
    
    FiberSubscribeOnPublisher(Flow.Publisher<? extends T> source, ExecutorPool pool) {
        this.source = source;
        this.pool = pool;
    }

    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        var worker = pool.worker();
        var parent = new SubscribeOnSubscriber<T>(subscriber, worker, source);
        parent.setFiber(FiberScope.background().schedule(worker, parent));
    }

    static final class SubscribeOnSubscriber<T> extends AtomicReference<Object> implements Flow.Subscriber<T>, Flow.Subscription, Callable<Void> {

        private static final long serialVersionUID = 334951501494781864L;

        final Flow.Subscriber<? super T> downstream;
        
        final ExecutorWorker worker;
        
        Subscription upstream;
        
        Flow.Publisher<? extends T> source;
        
        SubscribeOnSubscriber(Flow.Subscriber<? super T> downstream, ExecutorWorker worker, Flow.Publisher<? extends T> source) {
            this.downstream = downstream;
            this.worker = worker;
            this.source = source;
        }
        
        @Override
        public void request(long n) {
            upstream.request(n);
        }

        @Override
        public void cancel() {
            upstream.cancel();
            var old = getAndSet(this);
            if (old instanceof Fiber<?>) {
                ((Fiber<?>)old).cancel();
            }
            worker.close();
        }
        
        @Override
        public Void call() throws Exception {
            var source = this.source;
            this.source = null;
            source.subscribe(this);
            return null;
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            upstream = subscription;
            downstream.onSubscribe(this);
        }

        @Override
        public void onNext(T item) {
            downstream.onNext(item);
        }

        @Override
        public void onError(Throwable throwable) {
            downstream.onError(throwable);
            lazySet(this);
            worker.close();
        }

        @Override
        public void onComplete() {
            downstream.onComplete();
            lazySet(this);
            worker.close();
        }
        
        public void setFiber(Fiber<?> fiber) {
            if (get() != null || !compareAndSet(null, fiber)) {
                if (get() != this) {
                    fiber.cancel();
                }
            }
        }
    }
}
