package com.floweytf.coro.support;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

record Value<T>(T t) implements Result<T> {
    @Override
    public Optional<T> value() {
        return Optional.of(t);
    }

    @Override
    public Optional<Throwable> error() {
        return Optional.empty();
    }

    @Override
    public <U> Result<U> andThen(final Function<T, Result<U>> app) {
        return app.apply(t);
    }

    @Override
    public <U> Result<U> mapValue(final Function<T, U> app) {
        return new Value<>(app.apply(t));
    }

    @Override
    public <U> U mapBoth(final Function<T, U> valueMap, final Function<Throwable, U> errorMap) {
        return valueMap.apply(t);
    }

    @Override
    public void match(final Consumer<T> valueConsumer, final Consumer<Throwable> errorConsumer) {
        valueConsumer.accept(t);
    }
}
