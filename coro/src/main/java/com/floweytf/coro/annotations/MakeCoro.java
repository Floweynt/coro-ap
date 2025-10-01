package com.floweytf.coro.annotations;

import com.floweytf.coro.Co;
import com.floweytf.coro.concepts.CoroutineExecutor;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Supplier;

/**
 * Marks a specific parameter as implicitly coroutine.
 * <p>
 * This annotation is used with methods that take some {@link FunctionalInterface}, and wish to convert lambda
 * parameters to coroutines implicitly. This is important, since there is no other way to communicate to the java
 * compiler that a specific lambda should be a coroutine (we don't have access to annotations, so we can't annotate
 * the lambda as {@link Coroutine}). In fact, the only way to introduce a <i>lambda coroutine</i> is via
 * </p>
 *
 * <ol>
 *     <li>Using {@link Co#coroutine(Object)} [1]</li>
 *     <li>Annotating the callee formal parameter with {@link MakeCoro}</li>
 * </ol>
 *
 * <p>
 * This can be useful for user-provided methods to provide sugar, so callers don't need to explicitly specify that a
 * lambda should be a coroutine.
 * </p>
 *
 * <p>
 * [1] <i>note that in reality {@link MakeCoro} does all the heavy lifting for {@link Co#coroutine(Object)}</i>
 * </p>
 *
 * @see Co#coroutine(Object)
 */
@Target(ElementType.TYPE_USE)
@Retention(RetentionPolicy.CLASS)
public @interface MakeCoro {
}
