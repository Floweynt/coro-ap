package com.floweytf.coro.ap.util;

import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Symbol;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AnalyzerAdapter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public class Util {
    public static boolean isStatic(MethodNode node) {
        return (node.access & Opcodes.ACC_STATIC) != 0;
    }

    public static <T extends AccessibleObject> T setAccessible(@NotNull T accessor) {
        accessor.setAccessible(true);
        return accessor;
    }

    public static Method getMethod(@NotNull Class<?> clazz, @NotNull String mName,
                                   @NotNull Class<?>... parameterTypes) {
        Method method = null;
        Class<?> original = clazz;
        while (clazz != null) {
            try {
                method = clazz.getDeclaredMethod(mName, parameterTypes);
                break;
            } catch (NoSuchMethodException ignored) {
            }
            clazz = clazz.getSuperclass();
        }

        if (method == null) {
            throw new RuntimeException(original.getName() + " :: " + mName + "(args)");
        }

        return setAccessible(method);
    }

    public static Field getField(@NotNull Class<?> clazz, @NotNull String fName) {
        Field field = null;
        Class<?> original = clazz;
        while (clazz != null) {
            try {
                field = clazz.getDeclaredField(fName);
                break;
            } catch (NoSuchFieldException ignored) {
            }
            clazz = clazz.getSuperclass();
        }

        if (field == null) {
            throw new RuntimeException(original.getName() + " :: " + fName);
        }

        return setAccessible(field);
    }

    public static Symbol.ModuleSymbol getModule(Symbol.ClassSymbol symbol) {
        return symbol.owner.kind == Kinds.Kind.MDL ?
            (Symbol.ModuleSymbol) symbol.owner :
            symbol.packge().modle;
    }

    @FunctionalInterface
    public interface LocalVariableHandler {
        void accept(int index, @Nullable Type type, boolean isInit);
    }

    @FunctionalInterface
    public interface ArgumentVariableHandler {
        void accept(int index, Type type);
    }

    @FunctionalInterface
    public interface StackHandler {
        void accept(@Nullable Type type, boolean isInit);
    }

    public static void forEachLocal(AnalyzerAdapter frame, LocalVariableHandler handler) {
        for (int i = 0; i < frame.locals.size(); i++) {
            final var local = frame.locals.get(i);

            if (local == Opcodes.TOP) {
                continue;
            }

            if (local == Opcodes.INTEGER) {
                handler.accept(i, Type.INT_TYPE, true);
            } else if (local == Opcodes.FLOAT) {
                handler.accept(i, Type.FLOAT_TYPE, true);
            } else if (local == Opcodes.LONG) {
                handler.accept(i, Type.LONG_TYPE, true);
            } else if (local == Opcodes.DOUBLE) {
                handler.accept(i, Type.DOUBLE_TYPE, true);
            } else if (local == Opcodes.NULL) {
                handler.accept(i, null, true);
            } else if (local instanceof String name) {
                handler.accept(i, Type.getObjectType(name), true);
            } else if (local instanceof Label label) {
                label.getOffset();
            }
        }
    }

    public static void forEachStack(AnalyzerAdapter frame, int offset, StackHandler handler) {
        for (int i = frame.stack.size() - 1 - offset; i >= 0; i--) {
            final var stack = frame.stack.get(i);

            if (stack == Opcodes.TOP) {
                continue;
            }

            if (stack == Opcodes.INTEGER) {
                handler.accept(Type.INT_TYPE, true);
            } else if (stack == Opcodes.FLOAT) {
                handler.accept(Type.FLOAT_TYPE, true);
            } else if (stack == Opcodes.LONG) {
                handler.accept(Type.LONG_TYPE, true);
            } else if (stack == Opcodes.DOUBLE) {
                handler.accept(Type.DOUBLE_TYPE, true);
            } else if (stack == Opcodes.NULL) {
                handler.accept(null, true);
            } else if (stack instanceof String name) {
                handler.accept(Type.getObjectType(name), true);
            } else {
                throw new IllegalStateException("not implemented");
            }
        }
    }

    public static VarInsnNode loadLocal(int id, Type type) {
        return new VarInsnNode(
            switch (type.getSort()) {
                case Type.BOOLEAN, Type.CHAR, Type.BYTE, Type.SHORT, Type.INT -> Opcodes.ILOAD;
                case Type.FLOAT -> Opcodes.FLOAD;
                case Type.LONG -> Opcodes.LLOAD;
                case Type.DOUBLE -> Opcodes.DLOAD;
                case Type.ARRAY, Type.OBJECT -> Opcodes.ALOAD;
                default -> throw new AssertionError();
            },
            id
        );
    }

    public static VarInsnNode storeLocal(int id, Type type) {
        return new VarInsnNode(
            switch (type.getSort()) {
                case Type.BOOLEAN, Type.CHAR, Type.BYTE, Type.SHORT, Type.INT -> Opcodes.ISTORE;
                case Type.FLOAT -> Opcodes.FSTORE;
                case Type.LONG -> Opcodes.LSTORE;
                case Type.DOUBLE -> Opcodes.DSTORE;
                case Type.ARRAY, Type.OBJECT -> Opcodes.ASTORE;
                default -> throw new AssertionError();
            },
            id
        );
    }

    public static void withMethodBody(InsnList instructions, BiConsumer<LabelNode, LabelNode> handler) {
        final var startLabel = new LabelNode();
        final var endLabel = new LabelNode();

        instructions.insert(startLabel);
        instructions.add(endLabel);

        handler.accept(startLabel, endLabel);
    }

    public static List<Type> getAllMethodArgs(ClassNode owner, MethodNode method) {
        final var res = new ArrayList<>(Arrays.asList(Type.getMethodType(method.desc).getArgumentTypes()));

        if (!Util.isStatic(method)) {
            res.add(0, Type.getType("L" + owner.name + ";"));
        }

        return Collections.unmodifiableList(res);
    }

    public static void forEachArgType(List<Type> types, ArgumentVariableHandler handler) {
        int index = 0;
        for (Type type : types) {
            handler.accept(index, type);
            index += type.getSize();
        }
    }

    public static MethodInsnNode invokeInstr(int opc, ClassNode owner, MethodNode node) {
        return new MethodInsnNode(opc, owner.name, node.name, node.desc);
    }
}
