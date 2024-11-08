package com.floweytf.coro.support;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

record Error<T>(Throwable err) implements Result<T> {
    @Override
    public Optional<T> value() {
        return Optional.empty();
    }

    @Override
    public Optional<Throwable> error() {
        return Optional.of(err);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <U> Result<U> andThen(Function<T, Result<U>> app) {
        return (Result<U>) this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <U> Result<U> mapValue(Function<T, U> app) {
        return (Result<U>) this;
    }

    @Override
    public <U> U mapBoth(Function<T, U> valueMap, Function<Throwable, U> errorMap) {
        return errorMap.apply(err);
    }

    @Override
    public void match(Consumer<T> valueConsumer, Consumer<Throwable> errorConsumer) {
        errorConsumer.accept(err);
    }
}
