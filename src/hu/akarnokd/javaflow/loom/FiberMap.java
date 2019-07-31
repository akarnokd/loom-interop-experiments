package hu.akarnokd.javaflow.loom;

import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.Flow.*;
import java.util.concurrent.atomic.AtomicLong;

public final class FiberMap<T, R> implements Flow.Publisher<R> {

    final Flow.Publisher<? extends T> source;
    
    final FiberMapper<? super T, R> mapper;

    final ExecutorPool pool;
    
    final int prefetch;

    public FiberMap(Publisher<? extends T> source, FiberMapper<? super T, R> mapper, ExecutorPool pool, int prefetch) {
        this.source = source;
        this.mapper = mapper;
        this.pool = pool;
        this.prefetch = prefetch;
    }

    @Override
    public void subscribe(Subscriber<? super R> subscriber) {
        var worker = pool.worker();
        var parent = new MapperSubscriber<T, R>(subscriber, mapper, worker, prefetch);
        source.subscribe(parent);
        FiberScope.background().schedule(worker, parent);
    }
    
    static final class MapperSubscriber<T, R> extends AtomicLong implements Flow.Subscriber<T>, Flow.Subscription, Runnable, Emitter<R> {
        
        private static final long serialVersionUID = -8566124101708448290L;

        final Flow.Subscriber<? super R> downstream;
        
        final ExecutorWorker worker;
        
        final FiberMapper<? super T, R> mapper;
        
        Flow.Subscription upstream;
        
        final AtomicLong requested;
        
        final Queue<T> queue;
        
        final ResumableLock producerReady;
        
        final ResumableLock consumerReady;
        
        final long prefetch;
        
        volatile boolean done;
        Throwable error;
        
        volatile boolean cancelled;
        
        long produced;
        
        static final RuntimeException STOP = new RuntimeException("Flow cancelled");
        
        MapperSubscriber(Flow.Subscriber<? super R> downstream, FiberMapper<? super T, R> mapper, ExecutorWorker worker, long prefetch) {
            this.downstream = downstream;
            this.mapper = mapper;
            this.worker = worker;
            this.requested = new AtomicLong();
            this.queue = new ConcurrentLinkedQueue<T>();
            this.producerReady = new ResumableLock();
            this.consumerReady = new ResumableLock();
            this.prefetch = prefetch;
        }

        @Override
        public void run() {
            var limit = prefetch - (prefetch >> 2);
            var wip = 0L;
            var consumed = 0L;
            try {
                try {
                    for (;;) {
                        if (cancelled) {
                            break;
                        }
                        
                        var d = done;
                        var v = queue.poll();
                        var empty = v == null;
                        
                        if (d && empty) {
                            var ex = error;
                            if (ex != null) {
                                downstream.onError(ex);
                            } else {
                                downstream.onComplete();
                            }
                            break;
                        }
                        
                        if (!empty) {
                            ++wip;
                            // stable prefetch
                            if (++consumed == limit) {
                                consumed = 0;
                                upstream.request(limit);
                            }
                            
                            mapper.map(v, this);
                            
                            continue;
                        }

                        if (get() != wip) {
                            wip = addAndGet(-wip);
                        }
                        if (wip == 0L) {
                            producerReady.await();
                        }
                    }
                } catch (Throwable ex) {
                    // we have been cancelled?
                    if (!cancelled) {
                        downstream.onError(ex);
                    }
                }
            } finally {
                worker.close();
            }
        }

        @Override
        public void request(long n) {
            for (;;) {
                var current = requested.get();
                if (current == Long.MAX_VALUE) {
                    break;
                }
                var next = current + n;
                if (next < Long.MAX_VALUE) {
                    next = Long.MAX_VALUE;
                }
                if (requested.compareAndSet(current, next)) {
                    consumerReady.resume();
                    break;
                }
            }
        }

        @Override
        public void emit(R t) throws Throwable {
            var p = produced;
            if (requested.get() == p) {
                consumerReady.await();
            }
            if (cancelled) {
                throw STOP;
            }
            
            downstream.onNext(t);
            
            produced = p + 1;
        }

        @Override
        public void cancel() {
            cancelled = true;
            upstream.cancel();
            
            // unblock waiters
            producerReady.resume();
            consumerReady.resume();
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            upstream = subscription;
            downstream.onSubscribe(this);
            subscription.request(prefetch);
        }

        @Override
        public void onNext(T item) {
            queue.offer(item);
            if (getAndIncrement() == 0) {
                producerReady.resume();
            }
        }

        @Override
        public void onError(Throwable throwable) {
            error = throwable;
            onComplete();
        }

        @Override
        public void onComplete() {
            done = true;
            if (getAndIncrement() == 0) {
                producerReady.resume();
            }
        }
    }
}
