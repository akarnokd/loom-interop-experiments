package hu.akarnokd.javaflow.loom;

import static org.junit.Assert.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.atomic.*;

import org.junit.Test;

public class FiberPublisherTest {

    @Test
    public void singleStepConsume() {
        var p = new FiberPublisher<Integer>(emitter -> {
            for (int i = 0; i < 10; i++) {
                emitter.emit(i);
            }
        });
        
        var list = new ArrayList<Integer>();
        
        p.subscribe(new Flow.Subscriber<Integer>() {

            Subscription upstream;
            
            @Override
            public void onSubscribe(Subscription subscription) {
                this.upstream = subscription;
                subscription.request(1);
            }

            @Override
            public void onNext(Integer item) {
                System.out.println(item);
                list.add(item);
                upstream.request(1);
            }

            @Override
            public void onError(Throwable throwable) {
                throwable.printStackTrace();
            }

            @Override
            public void onComplete() {
                System.out.println("Done");
            }
        });
        
        assertEquals(Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), list);
    }

    @Test
    public void singleStepConsumeScheduled() {
        var p = new FiberPublisher<Integer>(emitter -> {
            try (var scope = FiberScope.open()) {
                for (int i = 0; i < 10; i++) {
                    var j = i;
                    emitter.emit(scope.schedule(() -> j).join());
                }
            }
        });
        
        var list = new ArrayList<Integer>();
        
        p.subscribe(new Flow.Subscriber<Integer>() {

            Subscription upstream;
            
            @Override
            public void onSubscribe(Subscription subscription) {
                this.upstream = subscription;
                subscription.request(1);
            }

            @Override
            public void onNext(Integer item) {
                System.out.println(item);
                list.add(item);
                upstream.request(1);
            }

            @Override
            public void onError(Throwable throwable) {
                throwable.printStackTrace();
            }

            @Override
            public void onComplete() {
                System.out.println("Done");
            }
        });
        
        assertEquals(Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), list);
    }

    @Test
    public void singleStepConsumeAsync() throws Exception {
        var p = new FiberPublisher<Integer>(emitter -> {
            for (int i = 0; i < 10; i++) {
                emitter.emit(i);
            }
        });

        var list = new ArrayList<Integer>();

        var exec = Executors.newSingleThreadExecutor();
        try {
            var cdl = new CountDownLatch(1);
            
            p.subscribe(new Flow.Subscriber<Integer>() {

                Subscription upstream;
                
                @Override
                public void onSubscribe(Subscription subscription) {
                    this.upstream = subscription;
                    exec.submit(() -> {
                        System.out.println("Requesting");
                        upstream.request(1);
                    });
                }

                @Override
                public void onNext(Integer item) {
                    System.out.println(item);
                    list.add(item);
                    exec.submit(() -> {
                        Thread.sleep(200);
                        list.add(-item);
                        System.out.println("Requesting");
                        upstream.request(1);
                        return null;
                    });
                }

                @Override
                public void onError(Throwable throwable) {
                    throwable.printStackTrace();
                    cdl.countDown();
                }

                @Override
                public void onComplete() {
                    System.out.println("Done");
                    cdl.countDown();
                }
            });
            
            assertTrue(cdl.await(30, TimeUnit.SECONDS));
        } finally {
            exec.shutdown();
        }
        
        assertEquals(Arrays.asList(
                0, 0, 
                1, -1, 
                2, -2, 
                3, -3, 
                4, -4, 
                5, -5,
                6, -6, 
                7, -7,
                8, -8,
                9), list);
    }

    @Test
    public void singleStepConsumeAsyncScheduled() throws Exception {
        var p = new FiberPublisher<Integer>(emitter -> {
            try (var scope = FiberScope.open()) {
                for (int i = 0; i < 10; i++) {
                    var j = i;
                    emitter.emit(scope.schedule(() -> j).join());
                }
            }
        });

        var list = new ArrayList<Integer>();

        var exec = Executors.newSingleThreadExecutor();
        try {
            var cdl = new CountDownLatch(1);
            
            p.subscribe(new Flow.Subscriber<Integer>() {

                Subscription upstream;
                
                @Override
                public void onSubscribe(Subscription subscription) {
                    this.upstream = subscription;
                    exec.submit(() -> {
                        System.out.println("Requesting");
                        upstream.request(1);
                    });
                }

                @Override
                public void onNext(Integer item) {
                    System.out.println(item);
                    list.add(item);
                    exec.submit(() -> {
                        Thread.sleep(200);
                        list.add(-item);
                        System.out.println("Requesting");
                        upstream.request(1);
                        return null;
                    });
                }

                @Override
                public void onError(Throwable throwable) {
                    throwable.printStackTrace();
                    cdl.countDown();
                }

                @Override
                public void onComplete() {
                    System.out.println("Done");
                    cdl.countDown();
                }
            });
            
            assertTrue(cdl.await(30, TimeUnit.SECONDS));
        } finally {
            exec.shutdown();
        }
        
        assertEquals(Arrays.asList(
                0, 0, 
                1, -1, 
                2, -2, 
                3, -3, 
                4, -4, 
                5, -5,
                6, -6, 
                7, -7,
                8, -8,
                9), list);
    }

    @Test
    public void singleStepCancel() throws Exception {
        var cleanup = new AtomicBoolean();
        var p = new FiberPublisher<Integer>(emitter -> {
            int i = 0;
            try {
                for (i = 0; i < 10; i++) {
                    emitter.emit(i);
                }
            } finally {
                cleanup.set(true);
            }
        });
        
        var list = new ArrayList<Integer>();
        
        p.subscribe(new Flow.Subscriber<Integer>() {

            Subscription upstream;
            
            @Override
            public void onSubscribe(Subscription subscription) {
                this.upstream = subscription;
                subscription.request(1);
            }

            @Override
            public void onNext(Integer item) {
                System.out.println(item);
                list.add(item);
                if (item == 5) {
                    upstream.cancel();
                } else {
                    upstream.request(1);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                throwable.printStackTrace();
            }

            @Override
            public void onComplete() {
                System.out.println("Done");
            }
        });

        assertTrue(cleanup.get());
        
        assertEquals(Arrays.asList(0, 1, 2, 3, 4, 5), list);
    }

    @Test
    public void singleStepAsyncCancel() throws Exception {
        var cleanup = new AtomicBoolean();
        var p = new FiberPublisher<Integer>(emitter -> {
            int i = 0;
            try {
                for (i = 0; i < 10; i++) {
                    emitter.emit(i);
                }
            } finally {
                cleanup.set(true);
            }
        });
        
        var list = new ArrayList<Integer>();
        var exec = Executors.newSingleThreadExecutor();
        try {
            p.subscribe(new Flow.Subscriber<Integer>() {
    
                Subscription upstream;
                
                @Override
                public void onSubscribe(Subscription subscription) {
                    this.upstream = subscription;
                    subscription.request(1);
                }
    
                @Override
                public void onNext(Integer item) {
                    System.out.println(item);
                    list.add(item);
                    if (item == 5) {
                        exec.submit(() -> {
                            Thread.sleep(200);
                            upstream.cancel();
                            return null;
                        });
                    } else {
                        upstream.request(1);
                    }
                }
    
                @Override
                public void onError(Throwable throwable) {
                    throwable.printStackTrace();
                }
    
                @Override
                public void onComplete() {
                    System.out.println("Done");
                }
            });

            Thread.sleep(500);
            
            assertTrue(cleanup.get());
            
            assertEquals(Arrays.asList(0, 1, 2, 3, 4, 5), list);
        } finally {
            exec.shutdown();
        }
    }
    

    @Test
    public void invalidRequest() throws Exception {
        var cleanup = new AtomicBoolean();
        var p = new FiberPublisher<Integer>(emitter -> {
            int i = 0;
            try {
                for (i = 0; i < 10; i++) {
                    emitter.emit(i);
                }
            } finally {
                cleanup.set(true);
            }
        });
        
        var list = new ArrayList<Integer>();
        var exec = Executors.newSingleThreadExecutor();
        var ex = new AtomicReference<Throwable>();
        
        try {
            p.subscribe(new Flow.Subscriber<Integer>() {
    
                Subscription upstream;
                
                @Override
                public void onSubscribe(Subscription subscription) {
                    this.upstream = subscription;
                    subscription.request(1);
                }
    
                @Override
                public void onNext(Integer item) {
                    System.out.println(item);
                    list.add(item);
                    if (item == 5) {
                        exec.submit(() -> {
                            Thread.sleep(200);
                            upstream.request(-1);
                            return null;
                        });
                    } else {
                        upstream.request(1);
                    }
                }
    
                @Override
                public void onError(Throwable throwable) {
                    ex.set(throwable);
                }
    
                @Override
                public void onComplete() {
                    System.out.println("Done");
                }
            });

            Thread.sleep(500);
            
            assertTrue(cleanup.get());
            
            assertNotNull(ex.get());
            assertTrue(ex.get() instanceof IllegalArgumentException);
            
            assertEquals(Arrays.asList(0, 1, 2, 3, 4, 5), list);
        } finally {
            exec.shutdown();
        }
    }
}
