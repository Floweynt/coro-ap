package com.floweytf.coro.test;

import com.floweytf.coro.Co;
import com.floweytf.coro.annotations.Coroutine;
import com.floweytf.coro.concepts.Task;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class MiscTests {
    @Coroutine
    public static Task<Integer> coReturnConstant() {
        return Co.ret(9);
    }

    @Coroutine
    public static Task<Long> testMergeLong(final boolean flag) {
        long value = 12;

        if (flag) {
            value = 11;
        } else {
            value = 13;
        }

        return Co.ret(value);
    }

    @Coroutine
    public static Task<Void> coThrowsException() {
        throw new IllegalStateException();
    }

    @Test
    public void testReturnValue() {
        Assertions.assertEquals(9, Co.launchBlocking(MiscTests::coReturnConstant));
    }

    @Test
    public void testCoroutineAwaitReturnValue() {
        Co.launchBlocking(() -> {
            Assertions.assertEquals(9, Co.await(coReturnConstant()));
            return Co.ret();
        });
    }

    @Test
    public void testConditionalValue() {
        Assertions.assertEquals(11, testMergeLong(true).begin().asFuture().join());
        Assertions.assertEquals(13, testMergeLong(false).begin().asFuture().join());
    }

    @Test
    @Timeout(5)
    public void testCoroutineWithExceptionHandling() {
        Co.launchBlocking(() -> {
            try {
                Co.await(coThrowsException());
            } catch (final IllegalStateException e) {
                return Co.ret(true);
            }

            return Co.ret(false);
        });
    }

    @Test
    @Timeout(5)
    public void testCoroutineThrowingException() {
        Assertions.assertThrows(CompletionException.class, () -> Co.launchBlocking(MiscTests::coThrowsException));
    }

    @Test
    @Timeout(5)
    public void testCoroutineFinallyBlock() {
        Assertions.assertThrows(CompletionException.class, () -> Co.launchBlocking(() -> {
            int i = 0;

            try {
                Co.await(coThrowsException());
            } finally {
                i = 4;
            }

            return Co.ret(i);
        }));
    }

    @Test
    @Timeout(5)
    public void testConstructor() {
        Co.launchBlocking(() -> {
            final var temp = new AtomicInteger(Co.await(coReturnConstant()));
            return Co.ret(temp);
        });
    }
}
