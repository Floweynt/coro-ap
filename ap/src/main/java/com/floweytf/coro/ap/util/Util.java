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
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import static com.floweytf.coro.ap.Constants.CLASS_TYPE_DESC;

public class Util {
    @FunctionalInterface
    public interface LocalVariableHandler {
        void accept(int index, @Nullable Type type, boolean isInit);
    }

    @FunctionalInterface
    public interface ArgumentVariableHandler {
        void accept(int index, Type type);
    }

    public sealed interface StackEntry permits StackEntry.Null, StackEntry.Regular, StackEntry.Uninitialized {
        Null NULL = new Null();

        final class Null implements StackEntry {
            private Null() {
            }
        }

        record Regular(Type type) implements StackEntry {
        }

        record Uninitialized(Type type, int count) implements StackEntry {
        }
    }

    @FunctionalInterface
    public interface StackHandler {
        void accept(StackEntry entry);
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
                handler.accept(new StackEntry.Regular(Type.INT_TYPE));
            } else if (stack == Opcodes.FLOAT) {
                handler.accept(new StackEntry.Regular(Type.FLOAT_TYPE));
            } else if (stack == Opcodes.LONG) {
                handler.accept(new StackEntry.Regular(Type.LONG_TYPE));
            } else if (stack == Opcodes.DOUBLE) {
                handler.accept(new StackEntry.Regular(Type.DOUBLE_TYPE));
            } else if (stack == Opcodes.NULL) {
                handler.accept(StackEntry.NULL);
            } else if (stack instanceof final String name) {
                handler.accept(new StackEntry.Regular(Type.getObjectType(name)));
            } else if (stack instanceof final Label label) {
                int count = 0;
                for (; i >= 0 && frame.stack.get(i) instanceof final Label l && l == label; i--) {
                    count++;
                }

                handler.accept(new StackEntry.Uninitialized(
                    Type.getObjectType((String) frame.uninitializedTypes.get(label)),
                    count
                ));
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

    private static AbstractInsnNode primitiveType(final Class<?> clazz) {
        return new FieldInsnNode(Opcodes.GETSTATIC, Type.getInternalName(clazz), "TYPE", CLASS_TYPE_DESC);
    }

    public static AbstractInsnNode ldc(final Object constant) {
        if (constant == null) {
            return new InsnNode(Opcodes.ACONST_NULL);
        }

        if (constant instanceof final Type type) {
            return switch (type.getSort()) {
                case Type.BOOLEAN -> primitiveType(Boolean.class);
                case Type.CHAR -> primitiveType(Character.class);
                case Type.BYTE -> primitiveType(Byte.class);
                case Type.SHORT -> primitiveType(Short.class);
                case Type.INT -> primitiveType(Integer.class);
                case Type.FLOAT -> primitiveType(Float.class);
                case Type.LONG -> primitiveType(Long.class);
                case Type.DOUBLE -> primitiveType(Double.class);
                default -> new LdcInsnNode(constant);
            };
        }

        return new LdcInsnNode(constant);
    }
}
