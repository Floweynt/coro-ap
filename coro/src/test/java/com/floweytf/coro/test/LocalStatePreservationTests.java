
package com.floweytf.coro.test;

import com.floweytf.coro.Co;
import com.floweytf.coro.annotations.Coroutine;
import com.floweytf.coro.concepts.Awaitable;
import com.floweytf.coro.concepts.CoroutineExecutor;
import com.floweytf.coro.concepts.Task;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalStatePreservationTests {
    @Coroutine
    private static Task<Void> parameterTask(final int param, final AtomicInteger result) {
        final var computed = param * 2;
        // Simulate suspension
        Co.await(Awaitable.from(CompletableFuture.completedFuture(null)));
        result.set(computed + 10);
        return Co.ret();
    }

    @Test
    @Timeout(5)
    void testLocalVariablePreservation() {
        final var finalValue = new AtomicInteger(0);

        final var future = new CompletableFuture<Void>();
        final var awaitable = Awaitable.from(future);

        CoroutineExecutor.EAGER.launch(() -> {
            var localCounter = 0;
            localCounter += 10;
            Co.await(awaitable);
            localCounter += 20;
            finalValue.set(localCounter);
            return Co.ret();
        });

        future.complete(null);

        assertEquals(30, finalValue.get());
    }

    @Test
    @Timeout(5)
    void testParameterPreservation() {
        final var result = new AtomicInteger(0);
        parameterTask(5, result).begin();
        assertEquals(20, result.get()); // (5 * 2) + 10 = 20
    }

    private record DummyClass(int value, String name) {
        @Override
        public @NotNull String toString() {
            return "DummyClass{value=" + value + ", name='" + name + "'}";
        }
    }

    @Coroutine
    private static Task<Void> dummyClassTest(final Awaitable<Void> awaitable, final AtomicReference<String> result) {
        // Create instance of dummy class
        final var dummy = new DummyClass(42, "test");
        Co.await(awaitable);
        // Reference dummy after suspension
        result.set(dummy.toString());
        return Co.ret();
    }

    @Coroutine
    private static Task<Void> twoSlotTypesTest(final Awaitable<Void> awaitable, final AtomicReference<String> result) {
        // Use long and double (both take 2 slots in JVM)
        final long longValue = 1234567890123L;
        final double doubleValue = 3.14159265359;
        Co.await(awaitable);
        // Reference both after suspension
        result.set("long=" + longValue + ", double=" + doubleValue);
        return Co.ret();
    }

    @Coroutine
    private static Task<Void> tryCatchScopeTest(final Awaitable<Void> awaitable, final AtomicReference<String> result
        , final AtomicBoolean exceptionCaught) {
        // Variable declared outside try block
        final var outerVar = new DummyClass(100, "outer");
        final long counter = 999L;

        try {
            Co.await(awaitable);
            // This might throw if awaitable completes exceptionally
        } catch (final Exception e) {
            exceptionCaught.set(true);
            // Reference variables from outer scope in catch block
            result.set("caught: " + outerVar + ", counter=" + counter);
        }
        return Co.ret();
    }

    @Coroutine
    private static Task<Void> complexLocalStateTest(final Awaitable<Void> awaitable,
                                                    final AtomicReference<String> result) {
        // Mix of different types including 2-slot types
        final byte byteVal = 127;
        final short shortVal = 32000;
        final int intVal = 1000000;
        final long longVal = 9876543210L;
        final float floatVal = 2.718f;
        final double doubleVal = 1.41421356;
        final char charVal = 'Z';
        final boolean boolVal = true;
        final var dummy = new DummyClass(777, "complex");

        Co.await(awaitable);

        // Verify all locals preserved
        result.set(String.format(
            "byte=%d, short=%d, int=%d, long=%d, float=%.3f, double=%.8f, char=%c, bool=%s, dummy=%s",
            byteVal, shortVal, intVal, longVal, floatVal, doubleVal, charVal, boolVal, dummy
        ));
        return Co.ret();
    }

    @Coroutine
    private static Task<Void> nestedScopeTest(final Awaitable<Void> awaitable, final AtomicReference<String> result) {
        final var outerDummy = new DummyClass(1, "outer");
        final long outerLong = 1111111111L;

        {
            final var innerDummy = new DummyClass(2, "inner");
            final double innerDouble = 2.5;
            Co.await(awaitable);
            // Mix inner and outer variables after suspension
            result.set("outer=" + outerDummy + ":" + outerLong + ", inner=" + innerDummy + ":" + innerDouble);
        }
        return Co.ret();
    }

    @Test
    @Timeout(5)
    void testDummyClassPreservation() {
        final var result = new AtomicReference<String>();
        final var future = new CompletableFuture<Void>();

        dummyClassTest(Awaitable.from(future), result).begin();
        future.complete(null);

        assertNotNull(result.get());
        assertEquals("DummyClass{value=42, name='test'}", result.get());
    }

    @Test
    @Timeout(5)
    void testTwoSlotTypesPreservation() {
        final var result = new AtomicReference<String>();
        final var future = new CompletableFuture<Void>();

        twoSlotTypesTest(Awaitable.from(future), result).begin();
        future.complete(null);

        assertNotNull(result.get());
        assertEquals("long=1234567890123, double=3.14159265359", result.get());
    }

    @Test
    @Timeout(5)
    void testTryCatchScopePreservation() {
        final var result = new AtomicReference<String>();
        final var exceptionCaught = new AtomicBoolean(false);
        final var future = new CompletableFuture<Void>();

        tryCatchScopeTest(Awaitable.from(future), result, exceptionCaught).begin();
        // Complete with exception to trigger catch block
        future.completeExceptionally(new RuntimeException("Test exception"));

        assertTrue(exceptionCaught.get());
        assertNotNull(result.get());
        assertEquals("caught: DummyClass{value=100, name='outer'}, counter=999", result.get());
    }

    @Test
    @Timeout(5)
    void testComplexLocalStatePreservation() {
        final var result = new AtomicReference<String>();
        final var future = new CompletableFuture<Void>();

        complexLocalStateTest(Awaitable.from(future), result).begin();
        future.complete(null);

        assertNotNull(result.get());
        assertEquals("byte=127, short=32000, int=1000000, long=9876543210, float=2.718, double=1.41421356, char=Z, " +
            "bool=true, dummy=DummyClass{value=777, name='complex'}", result.get());
    }

    @Test
    @Timeout(5)
    void testNestedScopePreservation() {
        final var result = new AtomicReference<String>();
        final var future = new CompletableFuture<Void>();

        nestedScopeTest(Awaitable.from(future), result).begin();
        future.complete(null);

        assertNotNull(result.get());
        assertEquals("outer=DummyClass{value=1, name='outer'}:1111111111, inner=DummyClass{value=2, name='inner'}:2" +
            ".5", result.get());
    }

    @Test
    @Timeout(5)
    void testMixedSuspensionPoints() {
        final var results = new AtomicReference<>(new String[3]);
        final var futures = List.of(
            new CompletableFuture<>(),
            new CompletableFuture<>(),
            new CompletableFuture<>()
        );

        final var multiSuspendTask = Co.makeTask(() -> {
            final var dummy1 = new DummyClass(1, "first");
            final long long1 = 1000L;
            Co.await(Awaitable.from(futures.get(0)));
            results.get()[0] = "1: " + dummy1 + ":" + long1;

            final var dummy2 = new DummyClass(2, "second");
            final double double2 = 1.234;
            Co.await(Awaitable.from(futures.get(1)));
            results.get()[1] = "2: " + dummy2 + ":" + double2;

            final var dummy3 = new DummyClass(3, "third");
            final long long3 = 3000L;
            final double double3 = 3.456;
            Co.await(Awaitable.from(futures.get(2)));
            results.get()[2] = "3: " + dummy3 + ":" + long3 + ":" + double3;

            return Co.ret();
        });

        multiSuspendTask.begin();

        // Complete futures in order
        for (int i = 0; i < futures.size(); i++) {
            assertNull(results.get()[i]); // Should not be set yet
            futures.get(i).complete(null);
            assertNotNull(results.get()[i]); // Should be set now
        }

        assertEquals("1: DummyClass{value=1, name='first'}:1000", results.get()[0]);
        assertEquals("2: DummyClass{value=2, name='second'}:1.234", results.get()[1]);
        assertEquals("3: DummyClass{value=3, name='third'}:3000:3.456", results.get()[2]);
    }
}