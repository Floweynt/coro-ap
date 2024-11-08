package com.floweytf.coro.concepts;

import com.floweytf.coro.annotations.Coroutine;
import org.jetbrains.annotations.ApiStatus;

/**
 * The execution context of a coroutine. This determines how and when continuations of a coroutine are executed.
 *
 * @see Coroutine
 */
@ApiStatus.OverrideOnly
public interface CoroutineExecutor {
    void executeTask(Runnable handler);

    CoroutineExecutor EAGER = Runnable::run;
}
