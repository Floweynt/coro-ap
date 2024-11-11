package com.floweytf.coro.support;

import com.floweytf.coro.Co;
import com.floweytf.coro.concepts.Awaitable;
import com.floweytf.coro.concepts.CoroutineExecutor;
import com.floweytf.coro.concepts.Task;
import java.util.concurrent.CompletableFuture;

/**
 * Utility class for manipulating awaitable and futures.
 */
public class Awaitables {
    private Awaitables() {
    }

    /**
     * Convert a future to an awaitable.
     *
     * @param future The future.
     * @param <T>    The return type of the future.
     * @return The awaitable that may be waited for with {@link Co#await(Awaitable)}
     */
    public static <T> Awaitable<T> awaitable(CompletableFuture<T> future) {
        return (executor, resume) -> future.whenComplete((value, throwable) -> {
            if (throwable != null) {
                resume.accept(Result.error(throwable));
            } else {
                resume.accept(Result.value(value));
            }
        });
    }

    /**
     * Executes a coroutine task with a different executor.
     *
     * @param executor The new executor.
     * @param task     The task to run on {@param executor}
     * @param <T>      The return type of the task.
     * @return An awaitable that runs the new task on {@param executor}.
     */
    public static <T> Awaitable<T> withExecutor(CoroutineExecutor executor, Task<T> task) {
        return (oldExecutor, resume) -> {
            task.begin(executor);
            task.onComplete(resume);
        };
    }

    /**
     * Starts an awaitable and converts it to a future.
     *
     * @param executor  The executor to start the awaitable on.
     * @param awaitable The awaitable.
     * @param <T>       The return type of the awaitable.
     * @return A java completable future that represents the execution of the awaitable.
     */
    public static <T> CompletableFuture<T> future(CoroutineExecutor executor, Awaitable<T> awaitable) {
        final var future = new CompletableFuture<T>();

        awaitable.suspend(executor, tResult -> tResult.match(
            future::complete,
            future::completeExceptionally
        ));

        return future;
    }

    /**
     * Starts an awaitable with the default {@link CoroutineExecutor#EAGER} executor and converts it to a future.
     *
     * @param awaitable The awaitable.
     * @param <T>       The return type of the awaitable.
     * @return A java completable future that represents the execution of the awaitable.
     */
    public static <T> CompletableFuture<T> future(Awaitable<T> awaitable) {
        return future(CoroutineExecutor.EAGER, awaitable);
    }
}
