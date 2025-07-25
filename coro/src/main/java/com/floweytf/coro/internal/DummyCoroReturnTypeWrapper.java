package com.floweytf.coro.internal;

import com.floweytf.coro.Co;
import com.floweytf.coro.concepts.Generator;
import com.floweytf.coro.concepts.Task;

/**
 * An internal dummy type used to make {@link Co#ret()} or {@link Co#ret(Object)} compile.
 *
 * @param <T>
 */
public interface DummyCoroReturnTypeWrapper<T> extends Task<Void>, Generator<T> {
}
