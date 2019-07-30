# loom-interop-experiments
Code to experiment with Project Loom continuation/fiber API.

## Features

Unless mentioned otherwise, all components use the Java `Flow` Reactive classes.

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

### FiberPublisher

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