package com.floweytf.coro.internal;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public record CoroutineMetadata(
    Class<?> declaringClass,
    int access,
    String methodName,
    Class<?>[] argTypes,
    @Nullable String fileName,
    int[] suspendPointLineNo
) {
}