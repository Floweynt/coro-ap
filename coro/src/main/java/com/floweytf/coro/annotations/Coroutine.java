package com.floweytf.coro.annotations;

import com.floweytf.coro.Co;
import com.floweytf.coro.concepts.Awaitable;
import com.floweytf.coro.concepts.CoroutineExecutor;
import com.floweytf.coro.concepts.Task;
import com.floweytf.coro.support.Awaitables;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a method as a coroutine. All coroutines are <i>stackless</i>.
 * <br>
 * A coroutine is a method that may be suspended. All methods annotated with {@code Coroutine} must return a
 * {@link Task}. In a coroutine, methods such as
 * {@link Co#await(Awaitable)} and {@link Co#ret()}/{@link Co#ret(Object)} become available,
 * allowing for imperative async code without callbacks. For example, an echo server could be implemented like:
 * <pre>{@code
 * while(socket.isOpen()) {
 *     final var data = Co.await(socket.read(512));
 *     Co.await(socket.write(data));
 * }
 * return Co.ret();
 * }</pre>
 * <br>
 * To return from a coroutine, you must use {@code return Co.ret([value]);}. Regular return statements are not accepted.
 * Void coroutines still must explicitly return once control flow reaches the end of the method. These limitations
 * are designed to ensure easy IDE integration without the use of any plugins.
 * <br>
 * The execution of a coroutine is determined by the {@link CoroutineExecutor} it is
 * started with in {@link Task#begin()}. The simplest executor eagerly executes the task on the same thread as the
 * completion. That is, if an {@link Awaitable} chooses to resume the coroutine on some arbitrary thread, the
 * execution of the coroutine will follow that.
 * <pre>{@code
 * public Awaitable<Void> switchThread() {
 *     return consumer -> new Thread().run(consumer.accept(null));
 * }
 *
 * @Coroutine
 * public Task<Void> example() {
 *     System.out.println("Hello from main thread"); // <- runs on main thread
 *     Co.await(switchThread());
 *     System.out.println("Hello from new thread"); // <- runs on the newly created thread
 * }
 *
 * public static void main(String... args) {
 *     example().begin();
 * }
 * }</pre>
 *
 * @see Awaitable
 * @see CoroutineExecutor
 * @see Task
 * @see Awaitables utility methods.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Coroutine {
}
