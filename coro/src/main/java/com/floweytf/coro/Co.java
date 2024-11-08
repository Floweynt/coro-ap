package com.floweytf.coro;

import com.floweytf.coro.concepts.Awaitable;
import com.floweytf.coro.internal.DummyCoroReturnTypeWrapper;

public class Co {
    private Co() {
    }

    public static <T> void yield(T value) {
        throw new AssertionError("Co.yield(T) should never be called directly; have you set up the AP properly?");
    }

    public static DummyCoroReturnTypeWrapper<Void> ret() {
        throw new AssertionError("Co.ret() should never be called directly; have you set up the AP properly?");
    }

    public static <T> DummyCoroReturnTypeWrapper<T> ret(T value) {
        throw new AssertionError("Co.ret(T) should never be called directly; have you set up the AP properly?");
    }

    public static <T> T await(Awaitable<T> awaitable) {
        throw new AssertionError("Co.await(T) should never be called directly; have you set up the AP properly?");
    }
}
