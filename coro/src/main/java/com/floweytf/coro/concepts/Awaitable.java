package com.floweytf.coro.concepts;

import com.floweytf.coro.Co;
import com.floweytf.coro.support.Result;
import java.util.concurrent.CompletionStage;
import org.jetbrains.annotations.ApiStatus;

/**
 * Represents an asynchronous task that can be awaited by a coroutine.
 *
 * <p>
 * An {@code Awaitable} represents a non-blocking, asynchronous operation. This operation performs some logic and,
 * once completed, invokes a callback function with either the result or an error.
 * </p>
 *
 * <p>
 * Example usage:
 * <pre>{@code
 * final var value = Co.await(readFile("..."));
 * System.out.println(value);
 * }</pre>
 *
 * <p>
 * This is roughly equivalent to the following code, which manually manages the continuation:
 * <pre>{@code
 * Consumer<String> continuation = value -> System.out.println(value);
 * readFile("...").suspend(value -> contextExecutor.executeTask(() -> continuation.accept(value)));
 * }</pre>
 *
 * @param <T> the type of the result produced by the asynchronous operation, which will be provided to the continuation
 *            callback via {@link Result}.
 * @see Co#await(Awaitable)
 */
@FunctionalInterface
@ApiStatus.OverrideOnly
public interface Awaitable<T> {
    /**
     * Converts a {@link CompletionStage} to an {@link Awaitable}.
     *
     * <p>
     * This method allows a {@link CompletionStage} to be used in a coroutine system by converting it into an
     * {@link Awaitable}, which can then be awaited using {@link Co#await(Awaitable)}.
     * </p>
     *
     * @param stage The {@link CompletionStage} to convert.
     * @param <T>   The type of the result that the {@link CompletionStage} produces.
     * @return An {@link Awaitable} that may be awaited using {@link Co#await(Awaitable)}.
     */
    static <T> Awaitable<T> from(final CompletionStage<T> stage) {
        return (executor, resume) -> stage.whenComplete((value, throwable) -> {
            if (throwable != null) {
                resume.submitError(throwable);
            } else {
                resume.submit(value);
            }
        });
    }

    /**
     * Suspends the execution of a coroutine until the asynchronous task represented by this {@code Awaitable} is
     * complete.
     *
     * <p>
     * This method is invoked when the coroutine encounters this {@code Awaitable}. The {@code resume} callback must
     * be called with the result of the task when it finishes, allowing the coroutine to resume execution. The
     * {@code executor} parameter provides the context in which the continuation should be executed. Do not dispatch
     * the execution of {@code result} on the executor. Instead, use this executor to dispatch additional work, or to
     * start an underlying coroutine.
     * </p>
     *
     * @param executor The {@link CoroutineExecutor} responsible for managing the execution context of the coroutine.
     * @param resume   A {@link Continuation} that accepts the result of the asynchronous task and resumes the coroutine
     *                 execution. The result is wrapped in a {@link Result} to handle both success and failure cases.
     */
    void execute(final CoroutineExecutor executor, Continuation<T> resume);

    /**
     * An "unwrapped" awaitable, which signals that the {@link Continuation} passed to
     * {@link Awaitable#execute(CoroutineExecutor, Continuation)} should not dispatch execution on the executor. This
     * is important for awaitables that provide some guarantees about the execution thread (for instance, awaitables
     * that dispatch resume onto a specific thread).
     *
     * <p>
     * This is an advanced concept for creating custom awaitables and should be used when the continuation is
     * guaranteed to be called from the correct thread context, making the executor's scheduling redundant.
     * </p>
     */
    @FunctionalInterface
    @ApiStatus.OverrideOnly
    interface Unwrapped<T> extends Awaitable<T> {
    }
}
