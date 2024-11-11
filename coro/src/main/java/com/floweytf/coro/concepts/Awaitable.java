package com.floweytf.coro.concepts;

import com.floweytf.coro.Co;
import com.floweytf.coro.support.Result;
import java.util.function.Consumer;
import org.jetbrains.annotations.ApiStatus;

/**
 * Represents some task may be awaited.
 * <br>
 * An awaitable abstractly represents some non-blocking/asynchronous task. An async task will typically perform some
 * logic, then invoke a callback function with either the result or and error.
 * <p>
 * As an example:
 * <pre>{@code
 * final var value = Co.await(readFile("..."))
 * System.out.println(value);
 * }</pre>
 * <p>
 * This translates to
 *
 * <pre>{@code
 * Consumer<String> continuation = value -> System.out.println(value);
 * readFile("...").suspend(value -> contextExecutor.executeTask(() -> continuation.accept(value)))
 * }</pre>
 *
 * @param <T> the type of the awaitable, also the result of {@link Co#await(Awaitable)}}.
 */
@FunctionalInterface
@ApiStatus.OverrideOnly
public interface Awaitable<T> {
    /**
     * Called when the coroutine begins to suspend.
     *
     * @param resume   The continuation function, invoke with the "return value" of this awaitable to continue
     *                 execution of the coroutine. Do not dispatch execution of resume on the executor.
     * @param executor The executor of the suspending coroutine.
     */
    void suspend(CoroutineExecutor executor, Consumer<Result<T>> resume);
}
