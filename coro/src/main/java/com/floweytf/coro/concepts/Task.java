package com.floweytf.coro.concepts;

import com.floweytf.coro.annotations.Coroutine;
import com.floweytf.coro.support.Result;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.jetbrains.annotations.ApiStatus;

/**
 * Represents the "external" handle for a coroutine that behaves like a task.
 *
 * <p>
 * This object implements the {@link Awaitable} interface, which means that any {@code Task} can be awaited. This allows
 * coroutines to call other coroutines, enabling composition of asynchronous tasks.
 * </p>
 *
 * <p>
 * A {@code Task} encapsulates the execution of a coroutine and provides methods to control its lifecycle, begin its
 * execution, add continuations, and more.
 * </p>
 *
 * @param <T> The return type of the coroutine associated with this {@code Task}.
 * @see Coroutine
 * @see Awaitable
 */
@ApiStatus.NonExtendable
public interface Task<T> extends Awaitable<T> {
    /**
     * Begins the execution of this coroutine task.
     *
     * <p>
     * This method starts the coroutine and ensures that it begins execution on the provided {@link CoroutineExecutor}.
     * It may involve scheduling work asynchronously or starting the task immediately, depending on the executor.
     * </p>
     *
     * @param executor The {@link CoroutineExecutor} to use for running this coroutine. The executor determines the
     *                 context  in which the coroutine is executed (e.g., on a particular thread, asynchronously, etc.).
     * @return {@code this}
     */
    Task<T> begin(CoroutineExecutor executor);

    /**
     * Begins the execution of this coroutine task using the default {@link CoroutineExecutor#EAGER}.
     *
     * <p>
     * This is a convenience method that starts the coroutine immediately on the current thread without scheduling it
     * asynchronously or on a different executor.
     * </p>
     *
     * @return {@code this}
     * @see CoroutineExecutor#EAGER
     */
    default Task<T> begin() {
        return begin(CoroutineExecutor.EAGER);
    }

    /**
     * Adds a continuation to be invoked once this task is completed.
     *
     * <p>
     * A continuation is a {@link Consumer} that will be invoked with the result of the task (success or failure). Any
     * continuations added will be invoked as soon as the task completes, including those added after the task starts.
     * </p>
     *
     * @param resume The continuation function, which will be called with the {@link Result} of this task when it
     *               completes. The result can be either successful or contain an error.
     */
    void onComplete(Consumer<Result<T>> resume);

    /**
     * {@inheritDoc}
     * <p>
     * This method begins the execution of the task and adds the provided continuation to be invoked once the task is
     * completed. The default behavior is to start the task using the specified {@code executor} and add the {@code
     * resume} callback as a continuation.
     * </p>
     *
     * @param executor The {@link CoroutineExecutor} to use for this coroutine.
     * @param resume   The continuation function to call when the task completes.
     */
    @Override
    void execute(CoroutineExecutor executor, Continuation<T> resume);

    default CompletableFuture<T> asFuture() {
        final var future = new CompletableFuture<T>();
        onComplete(res -> res.match(future::complete, future::completeExceptionally));
        return future;
    }
}