package com.floweytf.coro.ap.util;

import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Symbol;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
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

    public static boolean isStatic(final MethodNode node) {
        return (node.access & Opcodes.ACC_STATIC) != 0;
    }

    public static Symbol.ModuleSymbol getModule(final Symbol.ClassSymbol symbol) {
        return symbol.owner.kind == Kinds.Kind.MDL ?
            (Symbol.ModuleSymbol) symbol.owner :
            symbol.packge().modle;
    }

    public static void forEachLocal(final AnalyzerAdapter frame, final LocalVariableHandler handler) {
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
            } else if (local instanceof final String name) {
                handler.accept(i, Type.getObjectType(name), true);
            } else if (local instanceof final Label label) {
                handler.accept(i, Type.getObjectType((String) frame.uninitializedTypes.get(label)), false);
            }
        }
    }

    public static void forEachStack(final AnalyzerAdapter frame, final int offset, final StackHandler handler) {
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
            } else if (stack instanceof final String name) {
                handler.accept(Type.getObjectType(name), true);
            } else if (stack instanceof final Label label) {
                handler.accept(Type.getObjectType((String) frame.uninitializedTypes.get(label)), false);
            } else {
                throw new IllegalStateException("not implemented");
            }
        }
    }

    public static VarInsnNode loadLocal(final int id, final Type type) {
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

    public static VarInsnNode storeLocal(final int id, final Type type) {
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

    public static Object typeToFrameType(final Type type) {
        return switch (type.getSort()) {
            case Type.BOOLEAN, Type.CHAR, Type.BYTE, Type.SHORT, Type.INT -> Opcodes.INTEGER;
            case Type.FLOAT -> Opcodes.FLOAT;
            case Type.LONG -> Opcodes.LONG;
            case Type.DOUBLE -> Opcodes.DOUBLE;
            case Type.ARRAY, Type.OBJECT -> type.getInternalName();
            default -> throw new IllegalArgumentException("Bad type " + type.getInternalName() + " in frame");
        };
    }

    public static Object cloneFrameType(final Object type, final Map<LabelNode, LabelNode> mapper) {
        if (type instanceof final LabelNode node) {
            return mapper.get(node);
        }

        return type;
    }

    public static void withMethodBody(final InsnList instructions, final BiConsumer<LabelNode, LabelNode> handler) {
        final var startLabel = new LabelNode();
        final var endLabel = new LabelNode();

        instructions.insert(startLabel);
        instructions.add(endLabel);

        handler.accept(startLabel, endLabel);
    }

    public static List<Type> getAllMethodArgs(final ClassNode owner, final MethodNode method) {
        final var res = new ArrayList<>(Arrays.asList(Type.getMethodType(method.desc).getArgumentTypes()));

        if (!Util.isStatic(method)) {
            res.add(0, Type.getType("L" + owner.name + ";"));
        }

        return Collections.unmodifiableList(res);
    }

    public static void forEachArgType(final List<Type> types, final ArgumentVariableHandler handler) {
        int index = 0;
        for (final Type type : types) {
            handler.accept(index, type);
            index += type.getSize();
        }
    }

    public static MethodInsnNode invokeInstr(final int opc, final ClassNode owner, final MethodNode node) {
        return new MethodInsnNode(opc, owner.name, node.name, node.desc);
    }
}
