package com.floweytf.coro.internal;

import com.floweytf.coro.concepts.Generator;
import com.floweytf.coro.concepts.Task;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface DummyCoroReturnTypeWrapper<T> extends Task<Void>, Generator<T> {
}
