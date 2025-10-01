package com.floweytf.coro.test;

import com.floweytf.coro.Co;
import com.floweytf.coro.annotations.Coroutine;
import com.floweytf.coro.concepts.Awaitable;
import com.floweytf.coro.concepts.Task;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class IntegrationTests {

    @Coroutine
    private static Task<Integer> futureIntegrationTask(final Awaitable<Integer> awaitable) {
        final var value = Co.await(awaitable);
        return Co.ret(value * 2);
    }

    @Coroutine
    private static Task<Void> multiStepTask(final Awaitable<Void> step1, final Awaitable<Void> step2, final AtomicInteger stepCounter) {
        stepCounter.set(1);
        Co.await(step1);
        stepCounter.set(2);
        Co.await(step2);
        stepCounter.set(3);
        return Co.ret();
    }

    @Test
    @Timeout(5)
    void testCompletableFutureIntegration() {
        final var future = new CompletableFuture<Integer>();

        final var result = new AtomicInteger(0);
        
        futureIntegrationTask(Awaitable.from(future)).begin().onComplete(r -> r.match(
            result::set,
            error -> fail("Should not have failed")
        ));

        future.complete(21);
        assertEquals(42, result.get());
    }

    @Test
    @Timeout(5)
    void testMultipleAwaitPoints() {
        final var stepCounter = new AtomicInteger(0);
        final var step1 = new CompletableFuture<Void>();
        final var step2 = new CompletableFuture<Void>();

        multiStepTask(Awaitable.from(step1), Awaitable.from(step2), stepCounter).begin();

        assertEquals(1, stepCounter.get());
        step1.complete(null);
        assertEquals(2, stepCounter.get());
        step2.complete(null);
        assertEquals(3, stepCounter.get());
    }
}