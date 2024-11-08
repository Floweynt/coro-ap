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
    public <U> Result<U> andThen(Function<T, Result<U>> app) {
        return app.apply(t);
    }

    @Override
    public <U> Result<U> mapValue(Function<T, U> app) {
        return new Value<>(app.apply(t));
    }

    @Override
    public <U> U mapBoth(Function<T, U> valueMap, Function<Throwable, U> errorMap) {
        return valueMap.apply(t);
    }

    @Override
    public void match(Consumer<T> valueConsumer, Consumer<Throwable> errorConsumer) {
        valueConsumer.accept(t);
    }
}
