package com.floweytf.coro.test;

import com.floweytf.coro.Co;
import com.floweytf.coro.annotations.Coroutine;
import com.floweytf.coro.concepts.Awaitable;
import com.floweytf.coro.concepts.Task;
import com.floweytf.coro.support.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionHandlingTests {
    @Coroutine
    private static Task<Void> throwingTask() {
        throw new RuntimeException("Expected exception");
    }

    @Coroutine
    private static Task<Void> handlingTask(final AtomicBoolean exceptionCaught) {
        try {
            Co.await(throwingTask());
        } catch (final RuntimeException e) {
            exceptionCaught.set(true);
        }
        return Co.ret();
    }

    @Coroutine
    private static Task<Void> handlingTaskFinally(final AtomicBoolean exceptionCaught, final AtomicBoolean finallyExecuted) {
        try {
            Co.await(throwingTask());
        } catch (final RuntimeException e) {
            exceptionCaught.set(true);
        } finally {
            finallyExecuted.set(true);
        }
        return Co.ret();
    }

    @Coroutine
    private static Task<String> failingAwaitableTask(final Awaitable<String> failingAwaitable) {
        final var result = Co.await(failingAwaitable);
        return Co.ret(result);
    }

    @Test
    @Timeout(5)
    void testThrowingTask() {
        final var result = new AtomicReference<Result<Void>>();

        throwingTask().begin().onComplete(result::set);
        assertNotNull(result.get());
        assertTrue(result.get().hasError());
        assertInstanceOf(RuntimeException.class, result.get().error().get());
    }

    @Test
    @Timeout(5)
    void testExceptionCatchingInCoroutine() {
        final var exceptionCaught = new AtomicBoolean(false);

        final var result = new AtomicReference<Result<Void>>();
        handlingTask(exceptionCaught).begin().onComplete(result::set);

        assertNotNull(result.get());
        assertTrue(exceptionCaught.get());
        assertTrue(result.get().hasValue());
    }

    @Test
    @Timeout(5)
    void testExceptionCatchingInCoroutineWithFinally() {
        final var exceptionCaught = new AtomicBoolean(false);
        final var finallyExecuted = new AtomicBoolean(false);

        final var result = new AtomicReference<Result<Void>>();
        handlingTaskFinally(exceptionCaught, finallyExecuted).begin().onComplete(result::set);

        assertNotNull(result.get());
        assertTrue(exceptionCaught.get());
        assertTrue(finallyExecuted.get());
        assertTrue(result.get().hasValue());
    }

    @Test
    @Timeout(5)
    void testAwaitableExceptionPropagation() {
        final var future = new CompletableFuture<String>();

        final var result = new AtomicReference<Result<String>>();
        failingAwaitableTask(Awaitable.from(future)).begin().onComplete(result::set);

        future.completeExceptionally(new RuntimeException("Awaitable failed"));

        assertNotNull(result.get());
        assertTrue(result.get().hasError());
        assertEquals("Awaitable failed", result.get().error().get().getMessage());
    }
}