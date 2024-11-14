package com.floweytf;

import com.floweytf.coro.Co;
import com.floweytf.coro.annotations.Coroutine;
import com.floweytf.coro.concepts.Awaitable;
import com.floweytf.coro.concepts.Generator;
import com.floweytf.coro.concepts.Task;
import com.floweytf.coro.support.Result;

public class TestGenerator {
    @Coroutine
    public static Generator<Integer> runTests() {
        for(int i = 0; i < 100; i++) {
            Co.yield(i * i);
        }
        return Co.ret();
    }

    public static void main(String[] args) {
        runTests().stream().forEach(System.out::println);
    }
}