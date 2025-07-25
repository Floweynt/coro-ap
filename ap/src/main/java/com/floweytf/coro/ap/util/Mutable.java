package com.floweytf.coro.ap.util;

public interface Mutable<T> {
    T get();

    void set(T arg);

    static <T> Mutable<T> direct(final T init) {
        return new Mutable<>() {
            private T value = init;

            @Override
            public T get() {
                return value;
            }

            @Override
            public void set(T arg) {
                value = arg;
            }
        };
    }
}
