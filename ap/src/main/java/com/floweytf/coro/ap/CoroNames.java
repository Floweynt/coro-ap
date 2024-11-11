package com.floweytf.coro.ap;

import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

public record CoroNames(
    Name coroutineAnnotationName,
    Name coClassName,
    Name taskClassName,
    Name awaitName,
    Name yieldName,
    Name retName
) {
    CoroNames(Names names) {
        this(
            names.fromString(Constants.COROUTINE_ANN),
            names.fromString(Constants.CO_CLASS),
            names.fromString(Constants.TASK_CLASS),
            names.fromString(Constants.AWAIT_KW),
            names.fromString(Constants.YIELD_KW),
            names.fromString(Constants.RET_KW)
        );
    }
}
