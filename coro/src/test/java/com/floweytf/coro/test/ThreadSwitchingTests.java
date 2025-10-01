package com.floweytf.coro.test;

import com.floweytf.coro.Co;
import com.floweytf.coro.concepts.Awaitable;
import com.floweytf.coro.concepts.CoroutineExecutor;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ThreadSwitchingTests {
    @Test
    @Timeout(5)
    void testCompletableFutureThreadSwitching() throws InterruptedException {
        final var futureThread = new AtomicReference<Thread>();
        final var coroutineThread = new AtomicReference<Thread>();

        final var future = new CompletableFuture<Void>();

        final Awaitable<Void> awaitable = (executor, resume) -> future.whenComplete((result, error) -> {
            if (error != null) {
                resume.submitError(error);
            } else {
                resume.submit(result);
            }
        });

        final var testTask = Co.makeTask(() -> {
            coroutineThread.set(Thread.currentThread());
            Co.await(awaitable);
            futureThread.set(Thread.currentThread());
            return Co.ret();
        });

        // Start the task
        testTask.begin();

        // Complete the future from a different thread
        final var completerThread = new Thread(() -> future.complete(null), "completer-thread");
        completerThread.start();
        completerThread.join();

        assertNotNull(futureThread.get());
        assertNotNull(coroutineThread.get());
        assertEquals("completer-thread", futureThread.get().getName());
    }

    @Test
    @Timeout(5)
    void testUnwrappedAwaitableThreadPreservation() {
        final var initialThread = new AtomicReference<Thread>();
        final var resumedThread = new AtomicReference<Thread>();

        final Awaitable.Unwrapped<Void> unwrappedAwaitable = (executor, resume) -> resume.submit(null);

        final var task = Co.makeTask(() -> {
            initialThread.set(Thread.currentThread());
            Co.await(unwrappedAwaitable);
            resumedThread.set(Thread.currentThread());
            return Co.ret();
        });

        task.begin(CoroutineExecutor.fromExecutor(Executors.newSingleThreadExecutor())).asFuture().join();

        assertNotNull(initialThread.get());
        assertNotNull(resumedThread.get());
        // With Unwrapped, the thread should be the same as initial
        assertSame(initialThread.get(), resumedThread.get());
    }
}