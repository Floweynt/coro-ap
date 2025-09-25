package com.floweytf.coro.concepts;

import org.jetbrains.annotations.ApiStatus;

/**
 * The continuation handler for a coroutine.
 *
 * @param <T> The type to pass back into the coroutine, which is the result of {@code Co.await}.
 */
@ApiStatus.NonExtendable
public interface Continuation<T> {
    /**
     * Submits an error, equivalent to throwing an exception in the {@code Co.await}.
     *
     * @param error The exception to be thrown.
     */
    void submitError(Throwable error);

    /**
     * Submits the result of the awaitable.
     *
     * @param value The result.
     */
    void submit(T value);

    /**
     * A continuation that comes from a task.
     */
    interface Coroutine<T> extends Continuation<T> {
        /**
         * The instance of task that was suspended.
         */
        Task<T> theTask();

        /**
         * The code location of the {@code Co.await} call.
         */
        StackTraceElement calleeLocation();
    }
}
