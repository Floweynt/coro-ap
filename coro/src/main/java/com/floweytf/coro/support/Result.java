package com.floweytf.coro.support;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A result class representing a return value if success, or an exception if it was thrown.
 *
 * @param <T> The type of the return value.
 */
public sealed interface Result<T> permits Error, Value {
    /**
     * Constructs a successful result containing the provided value.
     *
     * @param value The value contained in the result, representing a successful outcome.
     * @param <T>   The type of the result's value.
     * @return A {@link Result} encapsulating the successful value.
     */
    static <T> Result<T> value(final T value) {
        return new Value<>(value);
    }

    /**
     * Constructs an unsuccessful result containing the provided error.
     *
     * @param err The error, typically an exception, representing a failed outcome.
     * @param <T> The type of the successful return value of the result (irrelevant in case of an error).
     * @return A {@link Result} encapsulating the error.
     */
    static <T> Result<T> error(final Throwable err) {
        return new Error<>(err);
    }

    /**
     * Obtains the underlying value, if present. This method is used when the result is a success.
     *
     * @return An {@link Optional} of the underlying value if present, or an empty {@link Optional} if this result is
     * an error.
     */
    Optional<T> value();

    /**
     * Obtains the underlying error, if present. This method is used when the result is an error.
     *
     * @return An {@link Optional} of the underlying error if present, or an empty {@link Optional} if this result is
     * a success.
     */
    Optional<Throwable> error();

    /**
     * Applies a function to the value of the result and returns a new {@link Result}.
     * If the current result is an error, it will propagate the error unchanged.
     *
     * @param <U> The type of the resulting value.
     * @param app The function to apply to the current value if it exists.
     * @return A new {@link Result} that is the result of applying the function to the value, or propagates the error.
     */
    <U> Result<U> andThen(Function<T, Result<U>> app);

    /**
     * Transforms the value inside the result using the provided function.
     * If the result is an error, the error will be preserved.
     *
     * @param <U> The type of the transformed value.
     * @param app The function to map the value of the current result.
     * @return A new {@link Result} with the transformed value, or the original error if it exists.
     */
    <U> Result<U> mapValue(Function<T, U> app);

    /**
     * Maps both the value and the error using separate functions.
     *
     * @param <U>      The type of the resulting mapped value.
     * @param valueMap The function to transform the value.
     * @param errorMap The function to transform the error.
     * @return A new result containing the mapped value or error.
     */
    <U> U mapBoth(Function<T, U> valueMap, Function<Throwable, U> errorMap);

    /**
     * Matches on the result, consuming either the value (if success) or the error (if failure).
     *
     * @param valueConsumer The consumer to handle the value if this result is a success.
     * @param errorConsumer The consumer to handle the error if this result is a failure.
     */
    void match(Consumer<T> valueConsumer, Consumer<Throwable> errorConsumer);

    /**
     * Returns true if the result contains a value, meaning it represents a successful outcome.
     *
     * @return true if the result contains a value, false if it contains an error.
     */
    default boolean hasValue() {
        return value().isPresent();
    }

    /**
     * Returns true if the result contains an error, meaning it represents a failure outcome.
     *
     * @return true if the result contains an error, false if it contains a value.
     */
    default boolean hasError() {
        return error().isPresent();
    }
}