package com.floweytf.coro.support;

import com.floweytf.coro.Co;
import com.floweytf.coro.concepts.Awaitable;
import com.floweytf.coro.concepts.CoroutineExecutor;
import com.floweytf.coro.concepts.Task;
import java.util.concurrent.CompletableFuture;

/**
 * Utility class for working with {@link Awaitable} and {@link CompletableFuture} objects in a coroutine-based system.
 * <p>
 * This class provides methods to convert between {@link CompletableFuture} and {@link Awaitable}, as well as a way to
 * execute coroutines on different {@link CoroutineExecutor}s and to convert coroutines into Java's standard
 * {@link CompletableFuture} for easier integration with non-coroutine code.
 */
public class Awaitables {
    private Awaitables() {
    }

    /**
     * Converts a {@link CompletableFuture} to an {@link Awaitable}.
     * <p>
     * This method allows a {@link CompletableFuture} to be used in a coroutine system by converting it into an
     * {@link Awaitable}, which can then be awaited using {@link Co#await(Awaitable)}.
     *
     * @param future The {@link CompletableFuture} to convert.
     * @param <T>    The type of the result that the {@link CompletableFuture} produces.
     * @return An {@link Awaitable} that may be awaited using {@link Co#await(Awaitable)}.
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
     * Executes a {@link Task} on a specified {@link CoroutineExecutor}.
     * <p>
     * This method allows a {@link Task} to be executed on a different {@link CoroutineExecutor} than the one it was
     * originally scheduled on. It returns an {@link Awaitable} that can be awaited within a coroutine.
     *
     * @param executor The {@link CoroutineExecutor} on which the {@link Task} should be executed.
     * @param task     The {@link Task} to execute.
     * @param <T>      The return type of the {@link Task}.
     * @return An {@link Awaitable} that runs the {@link Task} on the specified {@code executor}.
     */
    public static <T> Awaitable<T> withExecutor(CoroutineExecutor executor, Task<T> task) {
        return (oldExecutor, resume) -> {
            task.begin(executor); // Start the task with the new executor
            task.onComplete(resume); // Set up the continuation
        };
    }

    /**
     * Converts an {@link Awaitable} into a {@link CompletableFuture}.
     * <p>
     * This method runs the given {@link Awaitable} on the specified {@link CoroutineExecutor} and returns a
     * {@link CompletableFuture} that represents the result of the {@link Awaitable}.
     *
     * @param executor  The {@link CoroutineExecutor} to execute the {@link Awaitable} on.
     * @param awaitable The {@link Awaitable} to execute and convert to a {@link CompletableFuture}.
     * @param <T>       The result type of the {@link Awaitable}.
     * @return A {@link CompletableFuture} that represents the result of the {@link Awaitable}.
     */
    public static <T> CompletableFuture<T> future(CoroutineExecutor executor, Awaitable<T> awaitable) {
        final var future = new CompletableFuture<T>();

        awaitable.suspend(executor, tResult -> tResult.match(
            future::complete, // Complete the future if the result is successful
            future::completeExceptionally // Complete exceptionally if there was an error
        ));

        return future;
    }

    /**
     * Converts an {@link Awaitable} into a {@link CompletableFuture} using the default {@link CoroutineExecutor#EAGER}.
     * <p>
     * This is a convenience method that executes the {@link Awaitable} using the default eager executor
     * ({@link CoroutineExecutor#EAGER}), which runs the coroutine immediately on the current thread, and returns
     * a {@link CompletableFuture} that represents the result of the execution.
     *
     * @param awaitable The {@link Awaitable} to execute and convert to a {@link CompletableFuture}.
     * @param <T>       The result type of the {@link Awaitable}.
     * @return A {@link CompletableFuture} that represents the result of the {@link Awaitable}.
     * @see CoroutineExecutor#EAGER
     */
    public static <T> CompletableFuture<T> future(Awaitable<T> awaitable) {
        return future(CoroutineExecutor.EAGER, awaitable); // Use the default eager executor
    }
}
