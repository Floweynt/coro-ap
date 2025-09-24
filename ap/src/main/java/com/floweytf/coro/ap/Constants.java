package com.floweytf.coro.ap;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class Constants {
    public record MethodDesc(String owner, String name, String desc, boolean isInterface, Type returnType,
                             Type[] arguments) {
        public MethodDesc(final String owner, final String name, final Type returnType, final Type... arguments) {
            this(owner, name, Type.getMethodDescriptor(returnType, arguments), false, returnType, arguments);
        }

        public MethodInsnNode instr(final int opc) {
            return new MethodInsnNode(opc, owner, name, desc, isInterface);
        }

        public MethodNode node(final int access) {
            return new MethodNode(access, name, desc, null, null);
        }
    }

    public static final String COROUTINE_ANN = "com.floweytf.coro.annotations.Coroutine";
    public static final String DIAGNOSTIC_KEY = "com.floweytf.coro.ap";
    public static final String CO_CLASS = "com.floweytf.coro.Co";
    public static final String TASK_CLASS = "com.floweytf.coro.concepts.Task";
    public static final String AWAITABLE_CLASS = "com.floweytf.coro.concepts.Awaitable";
    public static final String GENERATOR_CLASS = "com.floweytf.coro.concepts.Generator";
    public static final String BASIC_TASK_CLASS = "com.floweytf.coro.internal.BasicTask";
    public static final String COROUTINE_EXECUTOR_CLASS = "com.floweytf.coro.concepts.CoroutineExecutor";
    public static final String CORO_METADATA_CLASS = "com.floweytf.coro.internal.CoroutineMetadata";

    public static final String AWAIT_KW = "await";
    public static final String RET_KW = "ret";
    public static final String COROUTINE_KW = "coroutine";
    public static final String CURRENT_EXECUTOR_KW = "currentExecutor";

    public static final Type OBJECT_TYPE = Type.getType(Object.class);
    public static final Type THROWABLE_TYPE = Type.getType(Throwable.class);
    public static final Type STRING_TYPE = Type.getType(String.class);
    public static final Type CLASS_TYPE = Type.getType(Class.class);

    public static final String CLASS_TYPE_BIN = CLASS_TYPE.getInternalName();
    public static final String CO_CLASS_BIN = CO_CLASS.replace('.', '/');
    public static final String BASIC_TASK_CLASS_BIN = BASIC_TASK_CLASS.replace('.', '/');
    public static final String CORO_METADATA_CLASS_BIN = CORO_METADATA_CLASS.replace('.', '/');
    public static final String OBJECT_CLASS_BIN = OBJECT_TYPE.getInternalName();
    public static final String THROWABLE_CLASS_BIN = THROWABLE_TYPE.getInternalName();

    public static final String CLASS_TYPE_DESC = CLASS_TYPE.getDescriptor();
    public static final String CORO_METADATA_CLASS_DESC = "L" + CORO_METADATA_CLASS_BIN + ";";
    public static final String COROUTINE_ANN_DESC = "L" + COROUTINE_ANN.replace(".", "/") + ";";

    // public SuspensionPointMetadata(int, String)
    public static final MethodDesc CORO_METADATA_CLASS_CTOR = new MethodDesc(
        CORO_METADATA_CLASS_BIN,
        "<init>",
        Type.VOID_TYPE,
        CLASS_TYPE,
        Type.INT_TYPE,
        STRING_TYPE,
        Type.getType(Class[].class),
        STRING_TYPE,
        Type.getType(int[].class)
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
    public static final String AWAITABLE_CLASS_BIN = AWAITABLE_CLASS.replace('.', '/');

    // protected static void checkThrow(final boolean isEx, final Object arg) throws Throwable {
    public static final MethodDesc BASIC_TASK_CHECK_THROW = new MethodDesc(
        BASIC_TASK_CLASS_BIN,
        "checkThrow",
        Type.VOID_TYPE,
        Type.BOOLEAN_TYPE,
        OBJECT_TYPE
    );

    // protected static <T, U> void suspendHelper(Awaitable<T> awaitable, BasicTask<U> self, int newState) {
    public static final MethodDesc BASIC_TASK_SUSPEND_HELPER = new MethodDesc(
        BASIC_TASK_CLASS_BIN,
        "suspendHelper",
        Type.VOID_TYPE,
        Type.getObjectType(AWAITABLE_CLASS_BIN),
        Type.getObjectType(BASIC_TASK_CLASS_BIN),
        Type.INT_TYPE
    );

    public static final String COROUTINE_EXECUTOR_CLASS_BIN = COROUTINE_EXECUTOR_CLASS.replace('.', '/');

    // protected CoroutineExecutor getExecutor() {
    public static final MethodDesc BASIC_TASK_GET_EXECUTOR = new MethodDesc(
        BASIC_TASK_CLASS_BIN,
        "getExecutor",
        Type.getObjectType(COROUTINE_EXECUTOR_CLASS_BIN)
    );
}
