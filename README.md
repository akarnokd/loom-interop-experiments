# loom-interop-experiments
Code to experiment with Project Loom continuation/fiber API.

## Features

Unless mentioned otherwise, all components use the Java `Flow` Reactive classes.

Table of contents

- [ContinuationPublisher](#continuationpublisher)
- [FiberPublisher](#fiberpublisher) & [FiberPublisherScoped](#fiberpublisherscoped)
- [FiberMap](#fibermap)
- [FiberConsumer](#fiberconsumer)
- [ExecutorPool](#executorpool) & [ExecutorWorker](#executorworker)
- [ResumableFiber](#resumablefiber)


### ContinuationPublisher

Uses the `Continuation` and `ContinuationScope` API to generate values and suspend on backpressure.

```java
var source = new ContinuationPublisher<Integer>(emitter -> {
    for (int i = 0; i < 10; i++) {
        emitter.accept(i);
    }
});

source.subscribe( ... );
```

Internally, suspension is triggered via `Continuation.yield()` when the requested amount is zero. The resumption is triggered via
`Continuation.run()` when the requested amount increases from zero to N. The sequence terminates when the lambda returns or throws.

### FiberPublisherScoped

Uses the FiberScope API to create a scope for each incoming `Subscriber` to generate the items via callback.

```java
var source = new FiberPublisher<Integer>((scope, emitter) -> {
    for (int i = 0; i < 10; i++) {
        var j = i;
        emitter.accept(
           scope.schedule(() -> j).join()
        );
     }
});

source.subscribe( ... );
```

The body of the lambda is executed for each incoming `Subscriber` on a fresh `FiberScope`, also provided to the lambda body.
Internally, suspension is based on awaiting lock condition (ReentrantLock.newCondition) when the requested amount is zero.
The resumption is triggered via a signal-all on the same lock condition when the requested amount increases from zero to N.
This type of logical blocking should only block the fiber, not any OS thread. The sequence terminates after the lambda returns or throws
and the scope is closed.

You can fork off computation via `scope.schedule` and `join` them back. Note however that calling `emitter.accept` from inside these scheduled
tasks is prohibited and may lead to undefined behavior due to races.

### FiberPublisher

Uses locks to to suspend the subscribing context when there is a lack of downstream requests.

```java
var source = new FiberPublisher<Integer>(emitter -> {
    try (var scope = FiberScope.open()) {
        for (int i = 0; i < 10; i++) {
            var j = i;
            emitter.emit(
               scope.schedule(() -> j).join()
            );
         }
     }
});

source.subscribe( ... );
```

If the `FiberPublisher` is not subscribed to in a fiber, it will block the caller thread. Use [FiberSubscribeOnPublisher](#fibersubscribeonpublisher) to
change the subscription to a fiber running on a specific backing Executor.

### FiberSubscribeOnPublisher

Subscribes to the upstream source while running inside a Fiber backed by a per-subscriber `Executor`.

```java
try (var pool = new ParrallelExecutorPool()) {
    var async = new FiberSubscribeOnPublisher<Integer>(source, pool);

    async.subscribe( ... );
    
    // await finishing of the subscriber
}
```

See [ExecutorPool](#executorpool) and [ExecutorWorker](#executorworker) about what and why the indirection around a plain `Executor` is needed

### FiberMap

Allows transforming each upstream item into zero or more values while running the transformation in a Fiber, thus allowing suspending code to run
as well as itself suspending on downstream backpressure. In order to support emitting any number of result items, the callback has to talk to an
`Emitter.emit()` call.

```java
try (var pool = new ParrallelExecutorPool()) {
    new FiberMap<Integer, String>(source, (value, emitter) -> {
        emitter.emit("" + value);
        emitter.emit("" + (value + 1));
    }, pool, 16);
}
```

The custom `FiberMapper` and `Emitter` interfaces are necessary because the underlying suspension mechanism, and in general, `Fiber.join()` can throw and would
make a `Consumer<T>` and standard functional interfaces work around not being able to throw checked exceptions.
Since mapping can take some arbitrary time, the `prefetch` parameter allows the upstream to generate some values while the mapper block is still working,
which improves the throughput of the setup.

### FiberConsumer

Runs a `Publisher` and through a returned `Iterator`, every next source items are made available upon each `next()` call in a fiber-blocking fashion.

```java
try (FiberScope scope = FiberScope.open()) {

    var sp = new SubmissionPublisher<Integer>();
    
    try (var iter = new FiberConsumer<>(sp).iterator()) {
        scope.schedule(() -> {
            for (int i = 1; i < 10; i++) {
                 sp.submit(i);
             }
             sp.close();
        });
        
        while (iter.hasNext()) {
            System.out.println(iter.next());
        }
    }
}
```

Unfortunately, the standard for-each over `Iterable` doesn't work because when the control would leave the iteration, the upstream subscription should be cancelled. Therefore, a custom `CloseableIterator` is returned to be used with the **try-with-resources** construct. 

### ExecutorPool

Fibers can be executed on any `Executor` and usually it is the `ForkJoinPool.commonPool()`. However, sometimes the number of carrier threads could be limited
to a handful of threads and perhaps each subscriber would like to run on a known and/or dedicated thread/fiber context.

Therefore, instead of using an `Executor` parameter in the `FiberXYZPublisher` operators, a two-step structure is employed, similar to how `Iterable`/`Iterator` is split.

An `ExecutorPool` is an auto-closeable resource which has one method, `worker()` to get an [`ExecutorWorker`](#executorworker) instance.
Operators will ask for this worker and have to release it eventually.

Currently, some pool implementations are available:

- `SingleExecutorPool` backed by a single-threaded standard executor service.
- `ParallelExecutorPool` backed by a number of single-threaded standard executor services, which are handed out on a round-robin fashion.
- `ForkJoinExecutorPool` backed by the `ForkJoinPool.commonPool`.

```java
try (var pool = new SingleExecutorPool()) {
    try (var worker = pool.worker()) {
        try (var scope = FiberScope.open()) {
        
             var v = scope.schedule(worker, () -> 1).join()
             
             System.out.println(v);
        }
    }
}
```

### ExecutorWorker

Represents a concrete `Executor` resource to be used with `FiberScope.schedule` calls for example. To support dynamic scaling of certain possible pool implementations, `ExecutorWorker` is auto-closeable and should be closed once there is no further need for it.

```java
class SomeOperation {
    final ExecutorWorker worker;
    
    SomeOperation(ExecutorWorker worker) {
         this.worker = worker;
    }
    
    void someMethod() {
    }
    
    void anotherMethod() {
    }
    
    void finalMethod() {
        worker.close();
     }
}
```

### ResumableFiber

A basic continuation primitive that can suspend and resume a Fiber (or Thread), usable for pausing on backpressure or data not available yet.

The most basic use is a ping-pong between fibers.

```java
var producerReady = new ResumableFiber();
var queue = new ConcurrentQueue<Integer>();
var done = new AtomicBoolean();

try (var scope = FiberScope.open()) {

    var job = scope.schedule(() -> {
        for (int i = 0; i < 1000; i++) {
             queue.offer(i);
             producerReady.resume();
        }
        done.set(true);
        producerReady.resume();
    ));

    while (!done.get()) {
        var v = queue.poll();
        if (v != null) {
            System.out.println("Got " + v);
            continue;
        }
        
        producerReady.await();
    }

    job.join();
}
```

In this example, the main fiber is polling on the shared queue and only suspending if it appears to be empty. The producer side, however,
has to indicate resume() after every offer in case the consumer is/was suspended. Calling `resume()` from multiple threads and multiple times
is allowed and won't by itself trigger multiple resumptions. 

However, similar to the queue-drain approach, state has to be prepared and properly released before calling `resume()` by using the appropriate
memory fences. In the example, `offer` does this.