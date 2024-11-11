package com.floweytf.coro.ap;

public class Constants {
    public static final String DIAGNOSTIC_KEY = "com.floweytf.coro.ap";
    public static final String COROUTINE_ANN = "com.floweytf.coro.annotations.Coroutine";
    public static final String CO_CLASS = "com.floweytf.coro.Co";
    public static final String TASK_CLASS = "com.floweytf.coro.concepts.Task";
    public static final String AWAITABLE_CLASS = "com.floweytf.coro.concepts.Awaitable";
    public static final String BASIC_TASK_CLASS = "com.floweytf.coro.internal.BasicTask";
    public static final String AWAIT = "await";
    public static final String YIELD = "yield";
    public static final String RET = "ret";

    public static final String COROUTINE_ANN_BIN = COROUTINE_ANN.replace('.', '/');
    public static final String CO_CLASS_BIN = CO_CLASS.replace('.', '/');
    public static final String BASIC_TASK_CLASS_BIN = BASIC_TASK_CLASS.replace('.', '/');
    public static final String TASK_CLASS_BIN = TASK_CLASS.replace('.', '/');
    public static final String AWAITABLE_CLASS_BIN = AWAITABLE_CLASS.replace('.', '/');

    // protected static <T, U> void suspendHelper(Awaitable<T> awaitable, BasicTask<U> self, int newState) {
    public static final String BASIC_TASK_CLASS_SUSPEND = "suspendHelper";
    public static final String BASIC_TASK_CLASS_SUSPEND_SIG = String.format(
        "(L%s;L%s;I)V",
        AWAITABLE_CLASS_BIN,
        BASIC_TASK_CLASS_BIN
    );

    public static final String BASIC_TASK_CLASS_COMPLETE_SUCCESS = "completeSuccess";
    public static final String BASIC_TASK_CLASS_COMPLETE_SUCCESS_SIG = "(Ljava/lang/Object;)V";

    // protected abstract void run(int state, boolean isExceptional, Object resVal);
    public static final String BASIC_TASK_CLASS_COMPLETE_RUN = "run";
    public static final String BASIC_TASK_CLASS_COMPLETE_RUN_SIG = "(IZLjava/lang/Object;)V";
}
