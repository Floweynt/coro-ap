package com.floweytf.coro.concepts;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.NonExtendable
public interface Continuation<T> {
    void submitError(Throwable error);

    void submit(T value);

    default void cancel() {
        submitError(new InterruptedException());
    }
}
