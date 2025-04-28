package com.floweytf;

import com.floweytf.coro.Co;
import com.floweytf.coro.annotations.Coroutine;
import com.floweytf.coro.concepts.Awaitable;
import com.floweytf.coro.concepts.Task;
import java.util.concurrent.atomic.AtomicInteger;

public class TestTask {
    private static final Awaitable<Void> SWITCH_THREAD = (executor, resume) -> new Thread(
        () -> resume.submit(null),
        "hi"
    ).start();

    @Coroutine
    public static Task<Void> test0() {
        System.out.println("Hello from thread: " + Thread.currentThread().getName());
        Co.await(SWITCH_THREAD);
        System.out.println("Hello from thread: " + Thread.currentThread().getName());
        return Co.ret();
    }

    @Coroutine
    public static Task<Void> test1() {
        final int a = 0;
        System.out.println("Hello from thread: " + Thread.currentThread().getName());
        Co.await(SWITCH_THREAD);
        System.out.println("Hello from thread: " + Thread.currentThread().getName());
        System.out.println("Local variable: " + a);
        return Co.ret();
    }

    @Coroutine
    public static Task<Void> test2() {
        final int a = 0;
        System.out.println("Hello from thread: " + Thread.currentThread().getName());
        Co.await((executor, resume) -> new Thread(
            () -> resume.submit(null),
            "hi"
        ).start());
        System.out.println("Hello from thread: " + Thread.currentThread().getName());
        System.out.println("Local variable: " + a);
        return Co.ret();
    }

    @Coroutine
    public static Task<Void> test3() {
        final int a = 0;
        System.out.println("Hello from thread: " + Thread.currentThread().getName());
        Co.await(SWITCH_THREAD);
        System.out.println("Hello from thread: " + Thread.currentThread().getName());
        System.out.println("Local variable: " + a);
        return Co.ret();
    }

    @Coroutine
    public static Task<Void> test4() {
        int a = 0;
        System.out.println("Hello from thread: " + Thread.currentThread().getName());
        a++;
        Co.await((executor, resume) -> new Thread(
            () -> resume.submit(null),
            "hi"
        ).start());
        System.out.println("Hello from thread: " + Thread.currentThread().getName());
        System.out.println("Local variable: " + a);
        return Co.ret();
    }

    @Coroutine
    public static Task<Void> test5(final int arg) {
        int a = 0;
        System.out.println("Hello from thread: " + Thread.currentThread().getName());
        a++;
        Co.await(SWITCH_THREAD);
        System.out.println("Hello from thread: " + Thread.currentThread().getName());
        System.out.println("Local variable: " + a);
        System.out.println("Argument variable: " + arg);
        Thread.dumpStack();
        return Co.ret();
    }

    @Coroutine
    public static Task<Void> test6(final int arg) {
        for (int i = 0; i < arg; i++) {
            System.out.println("Hello from thread: " + Thread.currentThread().getName());
            final int finalI = i;
            Co.await((executor,  resume) -> new Thread(
                () -> resume.submit(null),
                "hi " + finalI
            ).start());
            System.out.println("Hello from thread: " + Thread.currentThread().getName());
        }

        return Co.ret();
    }

    @Coroutine
    public static Task<Void> test7() {
        throw new IllegalStateException();
    }

    @Coroutine
    public static Task<Void> test8() {
        try {
            Co.await(test7());
        } catch (final IllegalStateException e) {
            System.out.println("caught exception!");
        }
        return Co.ret();
    }

    @Coroutine
    public static Task<Integer> test9() {
        return Co.ret(9);
    }

    @Coroutine
    public static Task<Void> test10() {
        System.out.println(Co.await(test9()));
        return Co.ret();
    }

    @Coroutine
    public static Task<Void> test11() {
        long value = 12;

        if(Boolean.getBoolean("flag")) {
            value = 11;
        } else {
            value = 13;
        }

        System.out.println(value);

        return Co.ret();
    }

    @Coroutine
    public static Task<Void> test12() {
        final var temp = new Test();
        Co.await(SWITCH_THREAD);
        System.out.println(temp);
        return Co.ret();
    }

    @Coroutine
    public static Task<Void> test13() {
        final var temp = new AtomicInteger(Co.await(test9()));
        System.out.println(temp);
        return Co.ret();
    }

    @Coroutine
    public static Task<Void> testMergeImpl(final boolean flag) {
        final Comparable<?> test;

        if (flag) {
            test = "test";
        } else {
            test = Co.await(test9());
        }

        System.out.println(test);
        return Co.ret();
    }

    @Coroutine
    public static Task<Void> runTests() {
        Co.await(test0());
        Co.await(test1());
        System.out.println(Co.currentExecutor());
        Co.await(test2());
        Co.await(test3());
        Co.await(test4());
        Co.await(test5(42));
        Co.await(test6(10));
        Co.await(testMergeImpl(true));
        Co.await(testMergeImpl(false));
        Co.await(test10());
        Co.await(test8());
        Co.await(new TestTask().memberCo());
        Co.await(test11());
        Co.await(test12());
        Co.await(test13());
        try {
            Co.await(test7());
        } finally {
            System.out.println("finally");
        }

        return Co.ret();
    }

    @Coroutine
    private Task<Void> memberCo() {
        System.out.println("Hello from thread: " + Thread.currentThread().getName());
        Co.await(SWITCH_THREAD);
        System.out.println("Hello from thread: " + Thread.currentThread().getName());
        System.out.println(this);
        return Co.ret();
    }

    public static void main(final String[] args) {
        runTests().begin();

        System.out.println(runTests());
    }
}