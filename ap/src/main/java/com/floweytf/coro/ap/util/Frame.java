package com.floweytf.coro.ap.util;

import java.util.Objects;
import java.util.function.UnaryOperator;

public final class Frame<T> {
    private T value;

    public Frame(final T value) {
        this.value = value;
    }

    public Frame() {
        this(null);
    }

    public void push(final T newValue, final Runnable action) {
        final var oldValue = value;
        value = newValue;
        try {
            action.run();
        } finally {
            value = oldValue;
        }
    }

    public T get() {
        return Objects.requireNonNull(value);
    }

    public void apply(final UnaryOperator<T> op) {
        value = op.apply(value);
    }

    public static <T1, T2> void push(
        final Frame<T1> f1, final Frame<T2> f2,
        final T1 t1, final T2 t2,
        final Runnable runnable
    ) {
        f1.push(t1, () -> f2.push(t2, runnable));
    }

    public static <T1, T2, T3> void push(
        final Frame<T1> f1, final Frame<T2> f2, final Frame<T3> f3,
        final T1 t1, final T2 t2, final T3 t3,
        final Runnable runnable
    ) {
        push(f1, f2, t1, t2, () -> f3.push(t3, runnable));
    }
}
