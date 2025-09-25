# Java Coroutines

A library/javac plugin for coroutines in java.

**DISCLAIMER**:
This is current not stable enough to be used in production. Bugs are likely. This is implemented as a javac plugin that
access javac's internals (com.sun), so will be unstable across java versions. I have not validated any compiler
toolchain except from my system JDK and the gradle wrapper (`javac 21.0.4`, `jdk21-openjdk 21.0.4.u7-1`, `gradle 8.8`).
Please report any java compatibility errors.

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

## Why not Loom?

Virtual threads are very flexible to integrate into existing codebases. However, coroutines provide much stronger
control over the specific execution policy of any given task, and ultimately are suitable for applications such as
single-threaded imperative state machines, generators, and much more.

## Performance

I have not benchmarked anything. Exception handling is not implemented efficiently, since each coroutine has
a method-global catch statement to propagate exceptions.

## Future Work

- Optimize the transformer to use streaming visitor api rather than asm tree