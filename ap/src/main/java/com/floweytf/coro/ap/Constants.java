package com.floweytf.coro.ap;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class Constants {
    public record MethodDesc(String owner, String name, String desc, boolean isInterface, Type returnType,
                             Type[] arguments) {
        public MethodDesc(String owner, String name, Type returnType, Type... arguments) {
            this(owner, name, Type.getMethodDescriptor(returnType, arguments), false, returnType, arguments);
        }

        public MethodDesc(String owner, String name, String desc) {
            this(owner, name, desc, false, Type.getReturnType(desc), Type.getArgumentTypes(desc));
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
    public static final String GENERATOR_CLASS = "com.floweytf.coro.concepts.Generator";
    public static final String BASIC_TASK_CLASS = "com.floweytf.coro.internal.BasicTask";
    public static final String BASIC_GENERATOR_CLASS = "com.floweytf.coro.internal.BasicGenerator";
    public static final String COROUTINE_EXECUTOR_CLASS = "com.floweytf.coro.concepts.CoroutineExecutor";
    public static final String AWAIT_KW = "await";
    public static final String YIELD_KW = "yield";
    public static final String RET_KW = "ret";
    public static final String CURRENT_EXECUTOR_KW = "currentExecutor";

    public static final Type OBJECT_TYPE = Type.getType(Object.class);
    public static final Type THROWABLE_TYPE = Type.getType(Throwable.class);

    public static final String COROUTINE_ANN_BIN = COROUTINE_ANN.replace('.', '/');
    public static final String CO_CLASS_BIN = CO_CLASS.replace('.', '/');
    public static final String BASIC_TASK_CLASS_BIN = BASIC_TASK_CLASS.replace('.', '/');
    public static final String TASK_CLASS_BIN = TASK_CLASS.replace('.', '/');
    public static final String GENERATOR_CLASS_BIN = GENERATOR_CLASS.replace('.', '/');
    public static final String BASIC_GENERATOR_CLASS_BIN = BASIC_GENERATOR_CLASS.replace('.', '/');
    public static final String AWAITABLE_CLASS_BIN = AWAITABLE_CLASS.replace('.', '/');
    public static final String COROUTINE_EXECUTOR_CLASS_BIN = COROUTINE_EXECUTOR_CLASS.replace('.', '/');
    public static final String OBJECT_CLASS_BIN = OBJECT_TYPE.getInternalName();
    public static final String THROWABLE_CLASS_BIN = THROWABLE_TYPE.getInternalName();

    // protected static <T, U> void suspendHelper(Awaitable<T> awaitable, BasicTask<U> self, int newState) {
    public static final MethodDesc BASIC_TASK_SUSPEND_HELPER = new MethodDesc(
        BASIC_TASK_CLASS_BIN,
        "suspendHelper",
        Type.VOID_TYPE,
        Type.getObjectType(AWAITABLE_CLASS_BIN),
        Type.getObjectType(BASIC_TASK_CLASS_BIN),
        Type.INT_TYPE
    );

    // protected static <T> void completeSuccess(T val, BasicTask<T> self)
    public static final MethodDesc BASIC_TASK_COMPLETE_SUCCESS = new MethodDesc(
        BASIC_TASK_CLASS_BIN,
        "completeSuccess",
        Type.VOID_TYPE,
        OBJECT_TYPE,
        Type.getObjectType(BASIC_TASK_CLASS_BIN)
    );

    // protected static <T> void completeError(Throwable val, BasicTask<T> self)
    public static final MethodDesc BASIC_TASK_COMPLETE_ERROR = new MethodDesc(
        BASIC_TASK_CLASS_BIN,
        "completeError",
        Type.VOID_TYPE,
        THROWABLE_TYPE,
        Type.getObjectType(BASIC_TASK_CLASS_BIN)
    );

    // protected abstract void run(int state, boolean isExceptional, Object resVal);
    public static final MethodDesc BASIC_TASK_RUN = new MethodDesc(
        BASIC_TASK_CLASS_BIN,
        "run",
        Type.VOID_TYPE,
        Type.INT_TYPE,
        Type.BOOLEAN_TYPE,
        OBJECT_TYPE
    );

    // protected CoroutineExecutor getExecutor() {
    public static final MethodDesc BASIC_TASK_GET_EXECUTOR = new MethodDesc(
        BASIC_TASK_CLASS_BIN,
        "getExecutor",
        Type.getObjectType(COROUTINE_EXECUTOR_CLASS_BIN)
    );

    public static final MethodDesc BASIC_TASK_CONSTRUCTOR = new MethodDesc(
        BASIC_GENERATOR_CLASS_BIN,
        "<init>", "()V"
    );

    // protected static <T> void yieldGenerator(Generator<T> generator, BasicGenerator<T> self, int newState)
    public static final MethodDesc BASIC_GENERATOR_YIELD_GENERATOR = new MethodDesc(
        BASIC_GENERATOR_CLASS_BIN,
        "yieldGenerator",
        Type.VOID_TYPE,
        Type.getObjectType(GENERATOR_CLASS_BIN),
        Type.getObjectType(BASIC_GENERATOR_CLASS_BIN),
        Type.INT_TYPE
    );

    // protected static <T> void yieldValue(T value, BasicGenerator<T> self, int newState)
    public static final MethodDesc BASIC_GENERATOR_YIELD_VALUE = new MethodDesc(
        BASIC_GENERATOR_CLASS_BIN,
        "yieldValue",
        Type.VOID_TYPE,
        OBJECT_TYPE,
        Type.getObjectType(BASIC_GENERATOR_CLASS_BIN),
        Type.INT_TYPE
    );

    // protected static <T> void completeError(Throwable value, BasicGenerator<T> self)
    public static final MethodDesc BASIC_GENERATOR_COMPLETE_ERROR = new MethodDesc(
        BASIC_GENERATOR_CLASS_BIN,
        "completeError",
        Type.VOID_TYPE,
        THROWABLE_TYPE,
        Type.getObjectType(BASIC_GENERATOR_CLASS_BIN)
    );

    // protected abstract void run(int state, Throwable ex);
    public static final MethodDesc BASIC_GENERATOR_RUN = new MethodDesc(
        BASIC_GENERATOR_CLASS_BIN,
        "run",
        Type.VOID_TYPE,
        Type.INT_TYPE,
        THROWABLE_TYPE
    );
}
