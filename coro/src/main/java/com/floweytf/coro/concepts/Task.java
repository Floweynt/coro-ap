package com.floweytf.coro.concepts;

import com.floweytf.coro.support.Result;
import com.floweytf.coro.annotations.Coroutine;
import java.util.function.Consumer;
import org.jetbrains.annotations.ApiStatus;

/**
 * Represents the "external" handle of a task-like coroutine.
 * <br>
 * This object implements the {@link Awaitable} interface, which means any Task is awaitable. This allows for calling
 * other coroutines from within a coroutine.
 *
 * @param <T> The return type of the coroutine.
 * @see Coroutine
 */
@ApiStatus.NonExtendable
public interface Task<T> extends Awaitable<T> {
    /**
     * Begins the execution of the
     *
     * @param executor The executor to use for this coroutine.
     */
    void begin(CoroutineExecutor executor);

    default void begin() {
        begin(CoroutineExecutor.EAGER);
    }

    /**
     * @param resume The resume method.
     */
    void onComplete(Consumer<Result<T>> resume);

    @Override
    default void suspend(CoroutineExecutor executor, Consumer<Result<T>> resume) {
        begin(executor);
        onComplete(resume);
    }
}
