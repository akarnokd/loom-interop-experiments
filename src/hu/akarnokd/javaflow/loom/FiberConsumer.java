package hu.akarnokd.javaflow.loom;

import java.util.NoSuchElementException;
import java.util.concurrent.Flow.*;
import java.util.concurrent.locks.*;

public final class FiberConsumer<T> {

    final Publisher<? extends T> source;

    public FiberConsumer(Publisher<? extends T> source) {
        this.source = source;
    }

    public CloseableIterator<T> iterator() {
        var ic = new IteratorConsumer<T>();
        source.subscribe(ic);
        return ic;
    }

    static final class IteratorConsumer<T> implements Subscriber<T>, CloseableIterator<T> {

        Subscription upstream;

        volatile boolean hasValue;
        T value;

        volatile boolean done;
        Throwable error;

        boolean consumerHasValue;
        T consumerValue;
        boolean consumerDone;

        final ReentrantLock producerLock;

        final Condition producerReady;

        boolean producerFlag;

        final ReentrantLock consumerLock;

        final Condition consumerReady;

        boolean consumerFlag;

        IteratorConsumer() {
            this.producerLock = new ReentrantLock();
            this.producerReady = producerLock.newCondition();
            this.consumerLock = new ReentrantLock();
            this.consumerReady = consumerLock.newCondition();
        }

        @Override
        public boolean hasNext() {
            if (consumerHasValue) {
                return true;
            }
            if (!consumerDone) {
                consumerReady();

                try {
                    producerAwait();
                } catch (InterruptedException ex) {
                    consumerDone = true;
                    throw new RuntimeException(ex);
                }

                consumerDone = done;
                var consumerError = error;

                consumerHasValue = hasValue;
                hasValue = false;
                consumerValue = value;
                value = null;


                if (!consumerHasValue && consumerError != null) {
                    throw new RuntimeException(consumerError);
                }

                return consumerHasValue;
            } else {
                var consumerError = error;
                if (consumerError != null) {
                    throw new RuntimeException(consumerError);
                }
            }
            return false;
        }

        @Override
        public T next() {
            if (consumerHasValue || hasNext()) {
                var v = consumerValue;
                consumerValue = null;
                consumerHasValue = false;
                return v;
            }
            throw new NoSuchElementException();
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            this.upstream = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(T item) {
            if (!done) {
                try {
                    consumerAwait();
                } catch (InterruptedException ex) {
                    upstream.cancel();
                    onError(ex);
                    return;
                }

                value = item;
                hasValue = true;

                producerReady();

                upstream.request(1);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            error = throwable;
            done = true;
            producerReady();
        }

        @Override
        public void onComplete() {
            done = true;
            producerReady();
        }

        @Override
        public void close() throws Exception {
            upstream.cancel();
            consumerReady(); // unblock producers
        }

        void consumerAwait() throws InterruptedException {
            consumerLock.lock();
            try {
                while (!consumerFlag) {
                    consumerReady.await();
                }
                consumerFlag = false;
            } finally {
                consumerLock.unlock();
            }
        }

        void consumerReady() {
            consumerLock.lock();
            try {
                consumerFlag = true;
                consumerReady.signalAll();
            } finally {
                consumerLock.unlock();
            }
        }

        void producerAwait() throws InterruptedException {
            producerLock.lock();
            try {
                while (!producerFlag) {
                    producerReady.await();
                }
                producerFlag = false;
            } finally {
                producerLock.unlock();
            }
        }

        void producerReady() {
            producerLock.lock();
            try {
                producerFlag = true;
                producerReady.signalAll();
            } finally {
                producerLock.unlock();
            }
        }
    }
}
