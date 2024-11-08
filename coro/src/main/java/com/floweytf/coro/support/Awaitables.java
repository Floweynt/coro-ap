package com.floweytf.coro.support;

import com.floweytf.coro.concepts.Awaitable;
import com.floweytf.coro.concepts.CoroutineExecutor;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class Awaitables {
    private Awaitables() {
    }

    public static <T> Awaitable<T> awaitable(CompletableFuture<T> future) {
        return (executor, resume) -> future.whenComplete((value, throwable) -> {
            if (throwable != null) {
                resume.accept(Result.error(throwable));
            } else {
                resume.accept(Result.value(value));
            }
        });
    }

    public static <T> CompletableFuture<T> future(CoroutineExecutor executor, Awaitable<T> awaitable) {
        final var future = new CompletableFuture<T>();

        awaitable.suspend(executor, tResult -> tResult.match(
            future::complete,
            future::completeExceptionally
        ));

        return future;
    }

    public static <T> CompletableFuture<T> future(Awaitable<T> awaitable) {
        return future(CoroutineExecutor.EAGER, awaitable);
    }
}
