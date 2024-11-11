package com.floweytf.coro;

import com.floweytf.coro.concepts.Awaitable;
import com.floweytf.coro.internal.DummyCoroReturnTypeWrapper;

/**
 * A utility class that provides support for coroutine-like functionality in Java.
 *
 * <p>This class is used to simulate coroutine keywords such as {@code co_await}, {@code co_return}, and {@code
 * co_yield} in a Java environment that does not natively support coroutines or these keywords. The methods in this
 * class are designed to be invoked instead of the equivalent coroutine keywords.
 *
 * <p>For example:
 * <ul>
 *   <li>Instead of {@code co_await expr}, use {@code Co.await(expr)}</li>
 *   <li>Instead of {@code co_return value}, use {@code return Co.ret(value)}</li>
 *   <li>Instead of {@code co_yield value}, use {@code Co.yield(value)}</li>
 * </ul>
 *
 * <p>All methods throw an {@link AssertionError} if called directly, indicating that the necessary annotation
 * processor (AP) for handling coroutine syntax has not been properly set up. These methods are only placeholders
 * that allow the code to compile when using coroutine features, but are expected to be processed and replaced by the
 * AP during compilation.
 */
public class Co {
    private Co() {
    }

    /**
     * Simulates the {@code co_yield} keyword by providing a placeholder method that throws an {@link AssertionError}
     * if called directly.
     *
     * <p>This method is used in place of the {@code co_yield} keyword in coroutine code. When called, it throws an
     * {@link AssertionError} with a message indicating that the annotation processor (AP) has not been properly
     * configured.
     *
     * @param value The value to "yield". This parameter is not used directly.
     * @param <T>   The type of the value being yielded.
     * @throws AssertionError If called directly, indicating the AP is not set up.
     */
    public static <T> void yield(T value) {
        throw new AssertionError("Co.yield(T) should never be called directly; have you set up the AP properly?");
    }

    /**
     * Simulates the {@code co_return} keyword by providing a placeholder method that returns a dummy value for void
     * return types. This method throws an {@link AssertionError} if called directly.
     *
     * <p>This method is used in place of the {@code co_return} keyword in coroutine code. When called, it throws an
     * {@link AssertionError} with a message indicating that the annotation processor (AP) has not been properly
     * configured.
     *
     * @return A dummy value representing a void return type. This method does not return a meaningful value and
     * exists solely to pass compilation.
     * @throws AssertionError If called directly, indicating the AP is not set up.
     */
    public static DummyCoroReturnTypeWrapper<Void> ret() {
        throw new AssertionError("Co.ret() should never be called directly; have you set up the AP properly?");
    }

    /**
     * Simulates the {@code co_return} keyword by providing a placeholder method that returns a dummy value for
     * non-void return types. This method throws an {@link AssertionError} if called directly.
     *
     * <p>This method is used in place of the {@code co_return} keyword in coroutine code. When called, it throws an
     * {@link AssertionError} with a message indicating that the annotation processor (AP) has not been properly
     * configured.
     *
     * @param value The value to return from the coroutine.
     * @param <T>   The type of the value being returned.
     * @return A dummy wrapper around the return value. This method does not return a meaningful value and exists
     * solely to pass compilation.
     * @throws AssertionError If called directly, indicating the AP is not set up.
     */
    public static <T> DummyCoroReturnTypeWrapper<T> ret(T value) {
        throw new AssertionError("Co.ret(T) should never be called directly; have you set up the AP properly?");
    }

    /**
     * Simulates the {@code co_await} keyword by providing a placeholder method that throws an {@link AssertionError}
     * if called directly.
     *
     * <p>This method is used in place of the {@code co_await} keyword in coroutine code. When called, it throws an
     * {@link AssertionError} with a message indicating that the annotation processor (AP) has not been properly
     * configured.
     *
     * @param awaitable The awaitable result to await.
     * @param <T>       The type of the result being awaited.
     * @return The result of awaiting the provided awaitable. This return value is not used directly and is a
     * placeholder for coroutine handling.
     * @throws AssertionError If called directly, indicating the AP is not set up.
     */
    public static <T> T await(Awaitable<T> awaitable) {
        throw new AssertionError("Co.await(T) should never be called directly; have you set up the AP properly?");
    }
}

