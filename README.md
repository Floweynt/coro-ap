# Java Coroutines

A library and javac plugin for stackless coroutines in Java. This project provides a way to write non-blocking,
asynchronous code in an imperative style. This also provides lightweight abstractions for imperative state machines.

## Project Status

**DISCLAIMER**: This library is not yet stable enough for production use. It is a proof-of-concept and bugs are likely.
The core of this library is a javac plugin that accesses internal `com.sun` APIs, which makes it brittle and potentially
unstable across different Java versions. It has been validated only with my local environment's JDK and the Gradle
wrapper (`javac 21.0.4`, `jdk21-openjdk 21.0.4.u7-1`, `gradle 8.8`). Please report any Java compatibility issues you
encounter.

## Concepts

This library is based on the concept of **stackless coroutines**. Unlike traditional functions, a coroutine can suspend
its execution and later be resumed from the point of suspension, without blocking the calling thread. The state of the
coroutine is managed explicitly, rather than being stored on the JVM stack.

The "core" of the API consists of:

- `@Coroutine`: An annotation that marks a method as a coroutine. The compiler plugin transforms these methods into
  state machines.
- `Task<T>`: The return type of coroutine, which represents the handle to the asynchronous operation.
- `Awaitable<T>`: An interface representing a unit of work that can be awaited within a coroutine.
- `Co.await(Awaitable<T>)`: A static method that "suspends" the coroutine, waiting for the `Awaitable` to
  complete. This is the core of the library.

## Implementation Details

The magic happens at compile time. The javac plugin intercepts the compilation of methods annotated with `@Coroutine`.
The plugin transforms the method's code into a state machine, replacing `Co.await()` calls with logic that saves the
current state (local variables, method arguments, etc.) and suspends execution. When the `Awaitable` completes, the
state machine is resumed, and execution continues from the saved state. This is a standard technique used by a variety
of languages.

## Usage

Add the javac plugin to the compiler's classpath with:

```kotlin
dependencies {
    annotationProcessor("com.floweytf.coro:ap:<latest-version>")
    implementation("com.floweytf.coro:coro:<latest-version>")
}
```

An example of coroutine usage that switches threads:

```java
public class Main {
    private static final Awaitable<Void> SWITCH_THREAD = (executor, resume) -> {
        new Thread(
            () -> resume.accept(Result.value(null)),
            "hi"
        ).start();
    };

    @Coroutine
    private static Task<Void> coroTask() {
        System.out.println("Current thread: " + Thread.currentThread().getName());
        Co.await(SWITCH_THREAD);
        System.out.println("Current thread: " + Thread.currentThread().getName());
        return Co.ret();
    }

    public static void main(String... args) {
        coroTask().begin();
    }
}
```

### Bundling

You may arbitrarily shade, relocate, and minimize the output jar, as all processing is done at compile time.

## Why not Loom?

Virtual threads, introduced by Project Loom, provides a flexible way to integrate lightweight concurrency into existing
codebases. This library, however, offers a distinct approach. Coroutines, as implemented here, provide much stronger
control over the specific execution policy of any given task. The `CoroutineExecutor` concept, for example, allows for
explicit control over where the coroutine's continuation will run. This makes them highly suitable for applications
requiring fine-grained control over execution, such as synchronizing a specific operation to a main thread. This
approach also supports other use cases such as (single-threaded) imperative state machines and generators. In general,
coroutines should be used when exact scheduling and thread context are necessary for correctness and performance. Unlike
virtual threads, which are scheduled by the JVM, a coroutine's execution can be tightly managed by the application logic
itself.

## Performance & Limitations

I have not benchmarked anything. Exception handling is not implemented efficiently, since each coroutine has a
method-global catch statement to propagate exceptions.

## Future Work & Contributing

- Optimize the transformer: The current transformer uses the ASM tree API, which is memory-intensive. A future
  improvement would be to use a streaming visitor API to reduce memory footprint and improve performance.
- Better CoroutineExecutor implementations: Develop more sophisticated `CoroutineExecutor`s for common patterns,
  such as a single-threaded event loop or a thread pool. This may be provided as an extension library.

### License & Contributing

This project is licensed under the LGPL License. Contributions are welcome! Please ensure that any pull requests adhere
to the existing code style and include relevant tests for new features.
