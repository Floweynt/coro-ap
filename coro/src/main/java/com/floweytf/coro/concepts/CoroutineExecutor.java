package com.floweytf.coro.concepts;

import com.floweytf.coro.annotations.Coroutine;
import com.floweytf.coro.annotations.NoThrow;
import java.util.concurrent.Executor;
import org.jetbrains.annotations.ApiStatus;

/**
 * Represents the execution context of a coroutine, responsible for managing how and when the continuation of a
 * coroutine is executed.
 *
 * <p>
 * A {@code CoroutineExecutor} defines the environment or thread in which the continuation (i.e., the next step of a
 * coroutine) will be executed. It controls when and how the task, suspended by the coroutine, should be resumed. This
 * abstraction allows for different scheduling behaviors and execution contexts.
 * </p>
 *
 * @see Coroutine
 */
@ApiStatus.OverrideOnly
public interface CoroutineExecutor {
    /**
     * Dispatches the execution of a coroutine continuation.
     * <p>
     * This method is called to execute a {@link Runnable} that represents the next step of a coroutine. The
     * continuation will be executed when the current coroutine suspends and is ready to resume. The manner in which
     * this task is dispatched depends on the specific {@code CoroutineExecutor} used (e.g., on a specific thread,
     * asynchronously, etc.).
     * </p>
     *
     * @param handler The task (continuation) to run. This is typically the next step or the callback to execute after
     *                a coroutine suspends.
     */
    @NoThrow
    void executeTask(Runnable handler);

    /**
     * Called before a task suspends.
     *
     * @param task      The task that is being suspended.
     * @param awaitable The awaitable to wait on.
     */
    @NoThrow
    default void onSuspend(final Task<?> task, final Awaitable<?> awaitable) {
    }

    /**
     * Called after a task resumes <i>normally</i>.
     *
     * @param task      The task that is to be resumed.
     * @param awaitable The awaitable that has just completed.
     * @param result    The result of the awaitable.
     * @param <T>       The result type of the awaitable.
     */
    @NoThrow
    default <T> void onResume(final Task<?> task, final Awaitable<T> awaitable, final T result) {
    }

    /**
     * Called after a task resumes <i>exceptionally</i>.
     *
     * @param task      The task that is to be resumed.
     * @param awaitable The awaitable that has just completed.
     * @param error     The error that was thrown.
     */
    @NoThrow
    default void onResumeExceptionally(final Task<?> task, final Awaitable<?> awaitable, final Throwable error) {
    }

    /**
     * A {@code CoroutineExecutor} that executes the continuation immediately on the current thread (eager execution).
     * <p>
     * This executor does not schedule the task asynchronously or on a separate thread. Instead, it runs the
     * continuation synchronously in the current execution context. This may be useful if your coroutine wishes to
     * manage its own scheduling behavior dynamically with specialized awaitables to modify the execution context.
     */
    CoroutineExecutor EAGER = new CoroutineExecutor() {
        @Override
        public void executeTask(final Runnable handler) {
            handler.run();
        }

        @Override
        public String toString() {
            return "CoroutineExecutor.EAGER";
        }
    };

    /**
     * Converts a standard java {@link Executor} to a {@link CoroutineExecutor}.
     *
     * @param executor The java executor.
     * @return The coroutine executor.
     */
    static CoroutineExecutor fromExecutor(final Executor executor) {
        return new CoroutineExecutor() {
            @Override
            public void executeTask(final Runnable handler) {
                executor.execute(handler);
            }

            @Override
            public String toString() {
                return "CoroutineExecutor[" + executor.toString() + "]";
            }
        };
    }
}
