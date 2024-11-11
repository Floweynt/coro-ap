package com.floweytf.coro.concepts;

import com.floweytf.coro.annotations.Coroutine;
import com.floweytf.coro.support.Result;
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
     * Begins the execution of this coroutine.
     *
     * @param executor The executor to use for this coroutine.
     */
    void begin(CoroutineExecutor executor);

    /**
     * Being the execution of the coroutine with the default {@link CoroutineExecutor#EAGER} executor.
     */
    default void begin() {
        begin(CoroutineExecutor.EAGER);
    }

    /**
     * Adds a continuation to this task. All continuations will be invoked as soon as this task is completed. Any
     * continuations added after will be immediately invoked.
     *
     * @param resume The resume method.
     */
    void onComplete(Consumer<Result<T>> resume);

    @Override
    default void suspend(CoroutineExecutor executor, Consumer<Result<T>> resume) {
        begin(executor);
        onComplete(resume);
    }
}
