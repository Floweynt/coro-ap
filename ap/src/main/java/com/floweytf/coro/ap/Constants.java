package com.floweytf.coro.ap;

import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class Constants {
    public record MethodDesc(String owner, String name, String desc, boolean isInterface) {
        public MethodDesc(String owner, String name, String desc) {
            this(owner, name, desc, false);
        }

        public MethodInsnNode instr(int opc) {
            return new MethodInsnNode(opc, owner, name, desc, isInterface);
        }

        public MethodNode node(int access) {
            return new MethodNode(access, name, desc, null, null);
        }
    }

    public static final String DIAGNOSTIC_KEY = "com.floweytf.coro.ap";
    public static final String COROUTINE_ANN = "com.floweytf.coro.annotations.Coroutine";
    public static final String CO_CLASS = "com.floweytf.coro.Co";
    public static final String TASK_CLASS = "com.floweytf.coro.concepts.Task";
    public static final String AWAITABLE_CLASS = "com.floweytf.coro.concepts.Awaitable";
    public static final String BASIC_TASK_CLASS = "com.floweytf.coro.internal.BasicTask";
    public static final String COROUTINE_EXECUTOR_CLASS = "com.floweytf.coro.concepts.CoroutineExecutor";
    public static final String AWAIT_KW = "await";
    public static final String YIELD_KW = "yield";
    public static final String RET_KW = "ret";
    public static final String CURRENT_EXECUTOR_KW = "currentExecutor";

    public static final String COROUTINE_ANN_BIN = COROUTINE_ANN.replace('.', '/');
    public static final String CO_CLASS_BIN = CO_CLASS.replace('.', '/');
    public static final String BASIC_TASK_CLASS_BIN = BASIC_TASK_CLASS.replace('.', '/');
    public static final String TASK_CLASS_BIN = TASK_CLASS.replace('.', '/');
    public static final String AWAITABLE_CLASS_BIN = AWAITABLE_CLASS.replace('.', '/');
    public static final String OBJECT_CLASS_BIN = Type.getInternalName(Object.class);
    public static final String COROUTINE_EXECUTOR_CLASS_BIN = COROUTINE_EXECUTOR_CLASS.replace('.', '/');
    public static final String THROWABLE_CLASS_BIN = Type.getInternalName(Throwable.class);

    // protected static <T, U> void suspendHelper(Awaitable<T> awaitable, BasicTask<U> self, int newState) {
    public static final MethodDesc BASIC_TASK_CLASS_SUSPEND = new MethodDesc(
        BASIC_TASK_CLASS_BIN,
        "suspendHelper",
        String.format(
            "(L%s;L%s;I)V",
            AWAITABLE_CLASS_BIN,
            BASIC_TASK_CLASS_BIN
        )
    );

    // protected void completeSuccess(T val) {
    public static final MethodDesc BASIC_TASK_CLASS_COMPLETE_SUCCESS = new MethodDesc(
        BASIC_TASK_CLASS_BIN,
        "completeSuccess",
        String.format(
            "(L%s;)V",
            OBJECT_CLASS_BIN
        )
    );

    // protected void completeError(Throwable val) {
    public static final MethodDesc BASIC_TASK_CLASS_COMPLETE_ERROR = new MethodDesc(
        BASIC_TASK_CLASS_BIN,
        "completeError",
        String.format(
            "(L%s;)V",
            THROWABLE_CLASS_BIN
        )
    );

    // protected abstract void run(int state, boolean isExceptional, Object resVal);
    public static final MethodDesc BASIC_TASK_CLASS_COMPLETE_RUN = new MethodDesc(
        BASIC_TASK_CLASS_BIN,
        "run",
        String.format(
            "(IZL%s;)V",
            OBJECT_CLASS_BIN
        )
    );

    // protected CoroutineExecutor getExecutor() {
    public static final MethodDesc BASIC_TASK_CLASS_GET_EXECUTOR = new MethodDesc(
        BASIC_TASK_CLASS_BIN,
        "getExecutor",
        String.format(
            "()L%s;",
            COROUTINE_EXECUTOR_CLASS_BIN
        )
    );

    public static final MethodDesc BASIC_TASK_CLASS_CONSTRUCTOR = new MethodDesc(
        BASIC_TASK_CLASS_BIN,
        "<init>", "()V"
    );
}
