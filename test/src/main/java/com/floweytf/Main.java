package com.floweytf;

import com.floweytf.coro.Co;
import com.floweytf.coro.annotations.Coroutine;
import com.floweytf.coro.concepts.Awaitable;
import com.floweytf.coro.concepts.Task;
import com.floweytf.coro.support.Result;

public class Main {
    private static final Awaitable<Void> SWITCH_THREAD = (executor, resume) -> new Thread(
        () -> resume.accept(Result.value(null)),
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
        int a = 0;
        System.out.println("Hello from thread: " + Thread.currentThread().getName());
        Co.await(SWITCH_THREAD);
        System.out.println("Hello from thread: " + Thread.currentThread().getName());
        System.out.println("Local variable: " + a);
        return Co.ret();
    }

    @Coroutine
    public static Task<Void> test2() {
        int a = 0;
        System.out.println("Hello from thread: " + Thread.currentThread().getName());
        Co.await((executor, resume) -> new Thread(
            () -> resume.accept(Result.value(null)),
            "hi"
        ).start());
        System.out.println("Hello from thread: " + Thread.currentThread().getName());
        System.out.println("Local variable: " + a);
        return Co.ret();
    }

    @Coroutine
    public static Task<Void> test3() {
        int a = 0;
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
            () -> resume.accept(Result.value(null)),
            "hi"
        ).start());
        System.out.println("Hello from thread: " + Thread.currentThread().getName());
        System.out.println("Local variable: " + a);
        return Co.ret();
    }

    @Coroutine
    public static Task<Void> test5(int arg) {
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
    public static Task<Void> test6(int arg) {
        for (int i = 0; i < arg; i++) {
            System.out.println("Hello from thread: " + Thread.currentThread().getName());
            int finalI = i;
            Co.await((executor, resume) -> new Thread(
                () -> resume.accept(Result.value(null)),
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
        } catch (IllegalStateException e) {
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
    public static Task<Void> testMergeImpl(boolean flag) {
        Comparable cloneable;

        if(flag) {
            cloneable = "test";
        } else {
            cloneable = 5;
        }

        System.out.println(cloneable);
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
        Co.await(test10());
        Co.await(test8());
        Co.await(new Main().memberCo());
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

    public static void main(String[] args) {
        runTests().begin().onComplete(x -> x.match(
            unused -> System.out.println("Returned normally"),
            throwable -> throwable.printStackTrace(System.out)
        ));
    }
}