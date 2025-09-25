package com.floweytf.coro;

import com.floweytf.coro.annotations.Coroutine;
import com.floweytf.coro.annotations.MakeCoro;
import com.floweytf.coro.concepts.Awaitable;
import com.floweytf.coro.concepts.CoroutineExecutor;
import com.floweytf.coro.concepts.Task;
import java.util.function.Supplier;

/**
 * A utility class that provides support for coroutine-like functionality in Java.
 *
 * <p>
 * This class is used to simulate coroutine keywords. The methods in this class are designed to be invoked instead
 * of the equivalent coroutine keywords.
 * </p>
 *
 * <p>
 * Most methods throw an {@link AssertionError} if called directly, indicating that the necessary annotation
 * processor (AP) for handling coroutine syntax has not been properly set up. These methods are only placeholders
 * that allow the code to compile when using coroutine features, but are expected to be processed and replaced by the
 * AP during compilation.
 * </p>
 */
public class Co {
    private Co() {
    }

    /**
     * Returns from a (void) coroutine.
     * <p>
     * In coroutine methods (i.e. methods annotated with {@link Coroutine}, or <i>coroutine-lambdas</i>), this method
     * should be used instead of a raw {@code return} to return from the method. For instance:
     * </p>
     * <pre>{@code
     * return Co.ret();
     * }</pre>
     *
     * @return A dummy value representing a void return type. This method does not return a meaningful value and
     * exists solely to pass compilation.
     * @throws AssertionError If the AP is not set up.
     */
    public static Task<Void> ret() {
        throw new AssertionError("Co.ret() should never be called directly; have you set up the AP properly?");
    }

    /**
     * Returns from a coroutine, with a specific value.
     * <p>
     * In coroutine methods (i.e. methods annotated with {@link Coroutine}, or <i>coroutine-lambdas</i>), this method
     * should be used instead of a raw {@code return} to return from the method. For instance:
     * </p>
     * <pre>{@code
     * return Co.ret(resultOfSomeComputation);
     * }</pre>
     *
     * @param value The value to return from the coroutine.
     * @param <T>   The type of the value being returned.
     * @return A dummy wrapper around the return value. This method does not return a meaningful value and exists
     * solely to pass compilation.
     * @throws AssertionError If the AP is not set up.
     */
    public static <T> Task<T> ret(final T value) {
        throw new AssertionError("Co.ret(T) should never be called directly; have you set up the AP properly?");
    }

    /**
     * Awaits on an {@link Awaitable}, suspending execution until it is resumed by the awaitable.
     *
     * <p>
     * Note that it is very much possible for execution to change threads after a call to this method. Proceed with
     * caution!
     * </p>
     *
     * @param awaitable The awaitable result to await.
     * @param <T>       The type of the result being awaited.
     * @return The result of awaiting the provided awaitable.
     * @throws AssertionError If the AP is not set up.
     */
    public static <T> T await(final Awaitable<T> awaitable) {
        throw new AssertionError("Co.await(T) should never be called directly; have you set up the AP properly?");
    }

    /**
     * Returns the current {@link CoroutineExecutor} for the executing coroutine.
     *
     * @return The current {@link CoroutineExecutor}.
     * @throws AssertionError If the AP is not set up.
     */
    public static CoroutineExecutor currentExecutor() {
        throw new AssertionError("Co.currentExecutor() should never be called directly; have you set up the AP " +
            "properly?");
    }

    /**
     * Makes any lambda expression passed in a <i>lambda coroutine</i>. This is a purely syntactic element.
     *
     * @param lambdaExpr The lambda expression.
     * @param <T>        The type of the lambda, which should be some {@link FunctionalInterface}.
     * @return the lambda, unchanged.
     * @see MakeCoro
     */
    public static <T> T coroutine(@MakeCoro final T lambdaExpr) {
        return lambdaExpr;
    }

    /**
     * Launches a coroutine.
     *
     * @param coroutine The coroutine to launch, which may be a <i>coroutine lambda</i>.
     * @param <T>       The result-type of the coroutine.
     * @return The task launched.
     * @see Task#begin()
     */
    public static <T> Task<T> launch(@MakeCoro final Supplier<Task<T>> coroutine) {
        return coroutine.get().begin();
    }

    /**
     * Launches a coroutine with a specific executor.
     *
     * @param executor  The executor to use when launching the coroutine.
     * @param coroutine The coroutine to launch, which may be a <i>coroutine lambda</i>.
     * @param <T>       The result-type of the coroutine.
     * @return The task launched.
     * @see Task#begin(CoroutineExecutor)
     */
    public static <T> Task<T> launch(final CoroutineExecutor executor, @MakeCoro final Supplier<Task<T>> coroutine) {
        return coroutine.get().begin(executor);
    }
}
