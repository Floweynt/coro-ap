package com.floweytf.coro.support;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public sealed interface Result<T> permits Error, Value {
    static <T> Result<T> value(T value) {
        return new Value<>(value);
    }

    static <T> Result<T> error(Throwable value) {
        return new Error<>(value);
    }

    Optional<T> value();

    Optional<Throwable> error();

    <U> Result<U> andThen(Function<T, Result<U>> app);

    <U> Result<U> mapValue(Function<T, U> app);

    <U> U mapBoth(Function<T, U> valueMap, Function<Throwable, U> errorMap);

    void match(Consumer<T> valueConsumer, Consumer<Throwable> errorConsumer);

    default boolean hasValue() {
        return value().isPresent();
    }

    default boolean hasError() {
        return error().isPresent();
    }
}
