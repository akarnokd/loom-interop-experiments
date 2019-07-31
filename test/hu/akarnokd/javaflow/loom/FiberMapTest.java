package hu.akarnokd.javaflow.loom;

import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

import org.junit.Test;

public class FiberMapTest {

    @Test
    public void basic() throws Exception {
        try (var pool = new SingleExecutorPool()) {
            try (var scope = FiberScope.open()) {
                var sp = new SubmissionPublisher<Integer>(ForkJoinPool.commonPool(), 4);
                
                var job1 = scope.schedule(() -> {
                    while (!sp.hasSubscribers()) {
                        Thread.sleep(1);
                    }
                    
                    for (int i = 0; i < 100; i++) {
                        System.out.println("Generating " + i);
                        sp.submit(i);
                    }
                    System.out.println("Generating Done");
                    sp.close();
                    return null;
                });
                
                
                var mapped = new FiberMap<Integer, Integer>(sp, (value, emitter) -> {
                    System.out.println("Mapping " + value);
                    emitter.emit(value);
                    System.out.println("Mapping " + (value + 1));
                    emitter.emit(value + 1);
                    System.out.println("Mapping " + (value + 2));
                    emitter.emit(value + 2);
                    System.out.println("Mapping [" + value + ".." + (value + 2) + "]");
                }, pool, Flow.defaultBufferSize());
                
                var list = new ArrayList<Integer>();
                var error = new AtomicReference<Throwable>();
                var limit = 2;
                
                var job2 = scope.schedule(() -> {

                    var latch = new CountDownLatch(1);
                    
                    mapped.subscribe(new Flow.Subscriber<Integer>() {

                        Flow.Subscription upstream;
                        
                        int remaining;
                        
                        @Override
                        public void onSubscribe(Subscription subscription) {
                            upstream = subscription;
                            System.out.println("Initial request");
                            subscription.request(limit);
                        }

                        @Override
                        public void onNext(Integer item) {
                            System.out.println("Receiving " + item);
                            list.add(item);
                            if (++remaining == limit) {
                                remaining = 0;
                                System.out.println("Requesting more");
                                upstream.request(limit);
                            }
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            error.set(throwable);
                            latch.countDown();
                        }

                        @Override
                        public void onComplete() {
                            latch.countDown();
                        }
                    });
                    
                    latch.await();
                    
                    return null;
                });
                
                job1.join();
                job2.join();
                
                assertEquals(300, list.size());
                
                for (int i = 0, j = 0; i < 100; i++, j += 3) {
                    assertEquals(i, list.get(j).intValue());
                    assertEquals(i + 1, list.get(j + 1).intValue());
                    assertEquals(i + 2, list.get(j + 2).intValue());
                }
            }
        }
    }
    

    @Test
    public void basic2() throws Exception {
        try (var pool = new SingleExecutorPool()) {
            try (var scope = FiberScope.open()) {
                var sp = new SubmissionPublisher<Integer>(ForkJoinPool.commonPool(), 4);
                
                var job1 = scope.schedule(() -> {
                    while (!sp.hasSubscribers()) {
                        Thread.sleep(1);
                    }
                    
                    for (int i = 0; i < 100; i++) {
                        System.out.println("Generating " + i);
                        sp.submit(i);
                    }
                    System.out.println("Generating Done");
                    sp.close();
                    return null;
                });
                
                
                var mapped = new FiberMap<Integer, Integer>(sp, (value, emitter) -> {
                    System.out.println("Mapping " + value);
                    emitter.emit(value);
                    System.out.println("Mapping " + (value + 1));
                    emitter.emit(value + 1);
                    System.out.println("Mapping " + (value + 2));
                    emitter.emit(value + 2);
                    System.out.println("Mapping [" + value + ".." + (value + 2) + "]");
                }, pool, 1);
                
                var list = new ArrayList<Integer>();
                var error = new AtomicReference<Throwable>();
                var limit = 2;
                
                var job2 = scope.schedule(() -> {

                    var latch = new CountDownLatch(1);
                    
                    mapped.subscribe(new Flow.Subscriber<Integer>() {

                        Flow.Subscription upstream;
                        
                        int remaining;
                        
                        @Override
                        public void onSubscribe(Subscription subscription) {
                            upstream = subscription;
                            System.out.println("Initial request");
                            subscription.request(limit);
                        }

                        @Override
                        public void onNext(Integer item) {
                            System.out.println("Receiving " + item);
                            list.add(item);
                            if (++remaining == limit) {
                                remaining = 0;
                                System.out.println("Requesting more");
                                upstream.request(limit);
                            }
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            error.set(throwable);
                            latch.countDown();
                        }

                        @Override
                        public void onComplete() {
                            latch.countDown();
                        }
                    });
                    
                    latch.await();
                    
                    return null;
                });
                
                job1.join();
                job2.join();
                
                assertEquals(300, list.size());
                
                for (int i = 0, j = 0; i < 100; i++, j += 3) {
                    assertEquals(i, list.get(j).intValue());
                    assertEquals(i + 1, list.get(j + 1).intValue());
                    assertEquals(i + 2, list.get(j + 2).intValue());
                }
            }
        }
    }
}
