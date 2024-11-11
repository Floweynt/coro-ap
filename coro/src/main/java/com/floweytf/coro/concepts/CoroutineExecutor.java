package com.floweytf.coro.concepts;

import com.floweytf.coro.annotations.Coroutine;
import org.jetbrains.annotations.ApiStatus;

/**
 * Represents the execution context of a coroutine, responsible for managing how and when the continuation of a
 * coroutine is executed.
 * <p>
 * A {@code CoroutineExecutor} defines the environment or thread in which the continuation (i.e., the next step of a
 * coroutine) will be executed. It controls when and how the task, suspended by the coroutine, should be resumed. This
 * abstraction allows for different scheduling behaviors and execution contexts.
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
     *
     * @param handler The task (continuation) to run. This is typically the next step or the callback to execute after
     *                a coroutine suspends.
     */
    void executeTask(Runnable handler);

    /**
     * A {@code CoroutineExecutor} that executes the continuation immediately on the current thread (eager execution).
     * <p>
     * This executor does not schedule the task asynchronously or on a separate thread. Instead, it runs the
     * continuation synchronously in the current execution context. This may be useful if your coroutine wishes to
     * manage its own scheduling behavior dynamically with specialized awaitables to modify the execution context.
     */
    CoroutineExecutor EAGER = Runnable::run;
}
