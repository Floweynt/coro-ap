package com.floweytf.coro.test;

import com.floweytf.coro.Co;
import com.floweytf.coro.annotations.Coroutine;
import com.floweytf.coro.concepts.Awaitable;
import com.floweytf.coro.concepts.Task;
import com.floweytf.coro.support.Result;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasicCoroutineTests {
    @Coroutine
    private static Task<Void> simpleTask() {
        return Co.ret();
    }

    @Coroutine
    private static Task<Integer> taskWithValue() {
        return Co.ret(42);
    }

    @Coroutine
    private static Task<String> taskWithAwaitable(final Awaitable<String> awaitable) {
        final var result = Co.await(awaitable);
        return Co.ret(result);
    }

    @Coroutine
    private static Task<Void> failingTask() {
        throw new RuntimeException("Test exception");
    }

    @Coroutine
    private static Task<Integer> innerTask() {
        return Co.ret(100);
    }

    @Coroutine
    private static Task<Integer> outerTask() {
        final var value = Co.await(innerTask());
        return Co.ret(value + 50);
    }

    @Test
    @Timeout(5)
    void testBasicCoroutineExecution() {
        final var completed = new AtomicBoolean(false);

        simpleTask().begin().onComplete(result -> completed.set(true));
        assertTrue(completed.get());
    }

    @Test
    @Timeout(5)
    void testCoroutineWithReturnValue() {
        final var result = new AtomicReference<Result<Integer>>();
        taskWithValue().begin().onComplete(result::set);

        assertNotNull(result.get());
        assertTrue(result.get().hasValue());
        assertEquals(42, result.get().value());
    }

    @Test
    @Timeout(5)
    void testAwaitableCompletion() {
        final var future = new CompletableFuture<String>();

        final var result = new AtomicReference<Result<String>>();
        taskWithAwaitable(Awaitable.from(future)).begin().onComplete(result::set);

        // Task should be suspended until future completes
        assertNull(result.get());

        future.complete("success");
        // Result should be available now
        assertNotNull(result.get());
        assertEquals("success", result.get().value());
    }

    @Test
    @Timeout(5)
    void testExceptionPropagation() {
        final var result = new AtomicReference<Result<Void>>();
        failingTask().begin().onComplete(result::set);

        assertNotNull(result.get());
        assertTrue(result.get().hasError());
        assertEquals("Test exception", result.get().error().get().getMessage());
    }

    @Test
    @Timeout(5)
    void testCoroutineComposition() {
        final var result = new AtomicReference<Result<Integer>>();
        outerTask().begin().onComplete(result::set);

        assertNotNull(result.get());
        assertEquals(150, result.get().value());
    }
}