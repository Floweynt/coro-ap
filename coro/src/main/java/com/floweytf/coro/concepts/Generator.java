package com.floweytf.coro.concepts;

import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.NonExtendable
public interface Generator<T> extends Iterable<T> {
    default Stream<T> stream() {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator(), 0), false);
    }
}