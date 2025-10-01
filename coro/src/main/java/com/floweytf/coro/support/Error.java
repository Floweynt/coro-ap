package com.floweytf.coro.support;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

record Error<T>(Throwable err) implements Result<T> {
    @Override
    public boolean hasValue() {
        return false;
    }

    @Override
    public T value() {
        throw new NoSuchElementException();
    }

    @Override
    public Optional<Throwable> error() {
        return Optional.of(err);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <U> Result<U> andThen(final Function<T, Result<U>> app) {
        return (Result<U>) this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <U> Result<U> mapValue(final Function<T, U> app) {
        return (Result<U>) this;
    }

    @Override
    public <U> U mapBoth(final Function<T, U> valueMap, final Function<Throwable, U> errorMap) {
        return errorMap.apply(err);
    }

    @Override
    public void match(final Consumer<T> valueConsumer, final Consumer<Throwable> errorConsumer) {
        errorConsumer.accept(err);
    }
}
