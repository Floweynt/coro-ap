package com.floweytf.coro.ap.codegen;

import com.floweytf.coro.ap.Constants;
import com.floweytf.coro.ap.util.Util;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AnalyzerAdapter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.util.TraceClassVisitor;

import static com.floweytf.coro.ap.Constants.CO_CLASS_BIN;
import static com.floweytf.coro.ap.Constants.RET_KW;
import static com.floweytf.coro.ap.Constants.THROWABLE_CLASS_BIN;
import static com.floweytf.coro.ap.Constants.THROWABLE_TYPE;

public abstract class BasicMethodTransformer {
    protected static final int LVT_THIS = 0;
    protected static final int LVT_STATE = 1;

    private final ClassNode coMethodOwner;
    private final MethodNode coMethod;

    private final ClassNode implClass;
    private final MethodNode implClassCons;
    private final MethodNode implClassToString;
    private final MethodNode implMethod;

    private final List<Type> argTypes;
    private final List<LabelNode> resumeLabels = new ArrayList<>();
    private final AnalyzerAdapter analyzer;
    private final FieldAllocator fieldAllocator = new FieldAllocator();

    private final int lvtScratch;
    private final int lvtOffset;
    private final Constants.MethodDesc handleException;

    private final Object[] initialLVTTypes;

    /**
     * A simple method transformer capable of handling coroutine methods.
     *
     * @param methodOwner The owner of the method.
     * @param coMethod    The method to transform.
     * @param id          An unique identifier.
     * @param lvtScratch  The scratch LVT variable index.
     * @param lvtOffset   The LVT offset to remap locals.
     */
    protected BasicMethodTransformer(final ClassNode methodOwner, final MethodNode coMethod, final int id,
                                     final int lvtScratch, final int lvtOffset,
                                     final Constants.MethodDesc handleException, final String superClass,
                                     final Constants.MethodDesc implMethodDesc) {
        this.coMethodOwner = methodOwner;
        this.lvtScratch = lvtScratch;
        this.lvtOffset = lvtOffset;
        this.handleException = handleException;
        this.argTypes = Util.getAllMethodArgs(methodOwner, coMethod);
        this.coMethod = coMethod;
        this.implClass = new ClassNode();
        this.implClassCons = new MethodNode(
            0,
            "<init>",
            Type.getMethodDescriptor(Type.VOID_TYPE, argTypes.toArray(Type[]::new)),
            null,
            coMethod.exceptions.toArray(String[]::new)
        );
        this.implClassToString = new MethodNode(
            Opcodes.ACC_PUBLIC,
            "toString",
            Type.getMethodDescriptor(Type.getType(String.class)),
            null,
            null
        );
        this.implMethod = implMethodDesc.node(Opcodes.ACC_PROTECTED);

        // set up necessary attributes to recognize our generated class as an inner class
        this.implClass.access = Opcodes.ACC_SUPER | Opcodes.ACC_SYNTHETIC;
        this.implClass.name = String.format("%s$%s$Coro$%d", methodOwner.name, coMethod.name, id);
        this.implClass.outerClass = methodOwner.name;
        this.implClass.superName = superClass;
        this.implClass.version = methodOwner.version;
        this.implClass.methods.add(implMethod);
        this.implClass.methods.add(implClassCons);
        this.implClass.methods.add(implClassToString);
        this.implClass.sourceFile = methodOwner.sourceFile;
        this.implClass.nestHostClass = methodOwner.name;
        this.implClass.outerMethod = coMethod.name;
        this.implClass.outerMethodDesc = coMethod.desc;
        this.implClass.innerClasses.add(new InnerClassNode(implClass.name, null, null, 0));

        methodOwner.innerClasses.add(new InnerClassNode(implClass.name, null, null, 0));
        if (methodOwner.nestMembers == null) {
            methodOwner.nestMembers = new ArrayList<>();
        }
        methodOwner.nestMembers.add(implClass.name);

        this.analyzer = new AnalyzerAdapter(methodOwner.name, coMethod.access, coMethod.name, coMethod.desc, null);

        this.initialLVTTypes = new Object[1 + implMethodDesc.arguments().length];

        initialLVTTypes[0] = implClass.name;
        for (int i = 0; i < implMethodDesc.arguments().length; i++) {
            initialLVTTypes[i + 1] = Util.typeToFrameType(implMethodDesc.arguments()[i]);
        }
    }

    private static boolean isCoMethod(final AbstractInsnNode node) {
        if (!(node instanceof final MethodInsnNode methodInsnNode)) {
            return false;
        }

        return node.getOpcode() == Opcodes.INVOKESTATIC && methodInsnNode.owner.equals(CO_CLASS_BIN);
    }

    protected FrameNode createNode(final Object... stack) {
        return new FrameNode(Opcodes.F_NEW, initialLVTTypes.length, initialLVTTypes, stack.length, stack);
    }

    private int remapLVT(final int lvt) {
        return lvt + lvtOffset;
    }

    private FieldInsnNode scratchField(final int opc, final int id, final Type value) {
        return new FieldInsnNode(opc, implClass.name, "l" + id, value.getDescriptor());
    }

    private FieldInsnNode argField(final int opc, final int id, final Type type) {
        return new FieldInsnNode(opc, implClass.name, "arg" + id, type.getDescriptor());
    }

    private Runnable suspendSaveLocals(final AnalyzerAdapter frame, final Object2IntMap<Type> allocMap) {
        final var output = implMethod.instructions;
        final var resumeInstr = new InsnList();

        if (frame.locals.isEmpty()) {
            return () -> {
            };
        }

        output.add(new VarInsnNode(Opcodes.ALOAD, LVT_THIS));
        resumeInstr.add(new VarInsnNode(Opcodes.ALOAD, LVT_THIS));

        Util.forEachLocal(frame, (localIndex, type, isInit) -> {
            if (type == null) { // handle the null literal type
                output.add(new InsnNode(Opcodes.POP));
                resumeInstr.add(new InsnNode(Opcodes.ACONST_NULL));
                resumeInstr.add(new VarInsnNode(Opcodes.ASTORE, remapLVT(localIndex)));
            } else if (!isInit) { // handle uninitialized reference type
                output.add(new InsnNode(Opcodes.POP));
                resumeInstr.add(new TypeInsnNode(Opcodes.NEW, type.getInternalName()));
                resumeInstr.add(new VarInsnNode(Opcodes.ASTORE, remapLVT(localIndex)));
            } else {
                final var fieldNum = fieldAllocator.getOrAllocateFieldId(type, allocMap);

                output.add(new InsnNode(Opcodes.DUP));
                output.add(Util.loadLocal(remapLVT(localIndex), type));
                output.add(scratchField(Opcodes.PUTFIELD, fieldNum, type));

                resumeInstr.add(new InsnNode(Opcodes.DUP));
                resumeInstr.add(scratchField(Opcodes.GETFIELD, fieldNum, type));
                resumeInstr.add(Util.storeLocal(remapLVT(localIndex), type));
            }
        });

        output.add(new InsnNode(Opcodes.POP));
        resumeInstr.add(new InsnNode(Opcodes.POP));

        return () -> output.add(resumeInstr);
    }

    private Runnable suspendSaveStack(final AnalyzerAdapter frame, final Object2IntMap<Type> allocMap) {
        final var resumeReversed = new ArrayList<AbstractInsnNode>();
        final var output = implMethod.instructions;

        if (frame.stack.size() > 1) {
            output.add(new VarInsnNode(Opcodes.ASTORE, lvtScratch));
            Util.forEachStack(frame, 1, (type, isInit) -> {
                if (type == null) { // handle the null literal type
                    output.add(new InsnNode(Opcodes.POP));
                    resumeReversed.add(new InsnNode(Opcodes.ACONST_NULL));
                } else if (!isInit) { // handle uninitialized reference type
                    output.add(new InsnNode(Opcodes.POP));
                    resumeReversed.add(new TypeInsnNode(Opcodes.NEW, type.getInternalName()));
                } else {
                    final var fieldId = fieldAllocator.getOrAllocateFieldId(type, allocMap);
                    output.add(new VarInsnNode(Opcodes.ALOAD, LVT_THIS));
                    if (type.getSize() == 2) {
                        output.add(new InsnNode(Opcodes.DUP_X2));
                    } else {
                        output.add(new InsnNode(Opcodes.DUP_X1));
                    }
                    output.add(new InsnNode(Opcodes.POP));
                    output.add(scratchField(Opcodes.PUTFIELD, fieldId, type));

                    // reverse order
                    resumeReversed.add(scratchField(Opcodes.GETFIELD, fieldId, type));
                    resumeReversed.add(new VarInsnNode(Opcodes.ALOAD, LVT_THIS));
                }
            });

            output.add(new VarInsnNode(Opcodes.ALOAD, lvtScratch));
        }

        return () -> {
            for (var i = resumeReversed.size() - 1; i >= 0; i--) {
                output.add(resumeReversed.get(i));
            }
        };
    }

    protected final void genSuspendPoint(final MethodInsnNode originalMethod) {
        final var output = implMethod.instructions;
        final var allocMap = new Object2IntArrayMap<Type>();
        final var resumeLabel = new LabelNode();

        final var resumeLocal = suspendSaveLocals(analyzer, allocMap);
        final var resumeStack = suspendSaveStack(analyzer, allocMap);

        output.add(new VarInsnNode(Opcodes.ALOAD, LVT_THIS));
        output.add(new LdcInsnNode(resumeLabels.size()));
        genReportSuspend(output, originalMethod);
        output.add(new InsnNode(Opcodes.RETURN));

        output.add(resumeLabel);

        output.add(createNode());
        genCheckException(output);

        resumeLocal.run();
        resumeStack.run();

        genResume(output);

        resumeLabels.add(resumeLabel);
    }

    private void genImplMethod() {
        final var output = implMethod.instructions;
        final var labelCloner = new Object2ObjectOpenHashMap<LabelNode, LabelNode>() {
            @Override
            public LabelNode get(final Object k) {
                var res = super.get(k);
                if (res == null) {
                    res = new LabelNode();
                    put((LabelNode) k, res);
                }
                return res;
            }
        };

        final var entryLabel = new LabelNode();
        resumeLabels.add(entryLabel);

        // stack state: [] -> []
        // lvt state: [<self-args>] -> [<self-args>, args]
        // entryLabel:
        //   aload this
        //   { dup, getfield, store }
        //   pop
        output.add(entryLabel);
        // this is the entry point, which means we have locals == args
        output.add(createNode());
        output.add(new VarInsnNode(Opcodes.ALOAD, LVT_THIS));
        Util.forEachArgType(argTypes, (index, argType) -> {
            output.add(new InsnNode(Opcodes.DUP));
            output.add(argField(Opcodes.GETFIELD, index, argType));
            output.add(Util.storeLocal(remapLVT(index), argType));
        });
        output.add(new InsnNode(Opcodes.POP));

        // process method body
        final var instructions = coMethod.instructions.toArray();
        for (var i = 0; i < instructions.length; i++) {
            final var instruction = instructions[i];

            if (instruction instanceof final VarInsnNode varInstNode) {
                // remap lvt for all variable instructions
                output.add(new VarInsnNode(varInstNode.getOpcode(), remapLVT(varInstNode.var)));
            } else if (instruction instanceof final IincInsnNode iincInsnNode) {
                // remap lvt for all variable instructions
                output.add(new IincInsnNode(remapLVT(iincInsnNode.var), iincInsnNode.incr));
            } else if (isCoMethod(instruction) && analyzer.stack != null) {
                final var methodInstr = (MethodInsnNode) instruction;

                if (methodInstr.name.equals(RET_KW)) {
                    if (i + 1 >= instructions.length || instructions[i + 1].getOpcode() != Opcodes.ARETURN) {
                        throw new AssertionError();
                    }
                    // stack state: [return_value:ref]
                    genReportReturn(output, methodInstr);
                    output.add(new InsnNode(Opcodes.RETURN));
                    i++;
                } else {
                    handleCoMethod(output, methodInstr);
                }
            } else if (instruction instanceof final FrameNode frameNode) {
                final var locals = new Object[lvtOffset + frameNode.local.size()];
                final var stack = new Object[frameNode.stack.size()];

                System.arraycopy(initialLVTTypes, 0, locals, 0, initialLVTTypes.length);
                Arrays.fill(locals, initialLVTTypes.length, lvtOffset, Opcodes.TOP);

                for (int off = 0; off < frameNode.local.size(); off++) {
                    locals[lvtOffset + off] = Util.cloneFrameType(frameNode.local.get(off), labelCloner);
                }

                for (int off = 0; off < frameNode.stack.size(); off++) {
                    stack[off] = Util.cloneFrameType(frameNode.stack.get(off), labelCloner);
                }

                output.add(new FrameNode(
                    Opcodes.F_NEW,
                    locals.length,
                    locals,
                    stack.length,
                    stack
                ));
            } else {
                // copy over the instruction
                output.add(instruction.clone(labelCloner));
            }

            // update the frame
            instruction.accept(analyzer);
        }

        // copy try-catch block, cloning labels
        // TODO: copy annotations
        coMethod.tryCatchBlocks.stream().map(block -> new TryCatchBlockNode(
            labelCloner.get(block.start),
            labelCloner.get(block.end),
            labelCloner.get(block.handler),
            block.type
        )).forEach(implMethod.tryCatchBlocks::add);

        // generate the switch table
        output.insert(new TableSwitchInsnNode(
            0,
            resumeLabels.size() - 1,
            entryLabel,
            resumeLabels.toArray(LabelNode[]::new)
        ));
        output.insert(new VarInsnNode(Opcodes.ILOAD, LVT_STATE));

        // generate the exception handler
        Util.withMethodBody(output, (start, end) -> {
            final var catcher = new LabelNode();
            output.add(catcher);
            output.add(createNode(THROWABLE_CLASS_BIN));
            output.add(new VarInsnNode(Opcodes.ALOAD, LVT_THIS));
            output.add(handleException.instr(Opcodes.INVOKESTATIC));
            output.add(new InsnNode(Opcodes.RETURN));
            implMethod.tryCatchBlocks.add(new TryCatchBlockNode(start, end, catcher, THROWABLE_TYPE.getInternalName()));
        });

        // handle local variables
        Util.withMethodBody(output, (start, end) -> {
            if (coMethod.localVariables == null) {
                implMethod.localVariables = new ArrayList<>();
            } else {
                implMethod.localVariables = coMethod.localVariables.stream().map(old -> new LocalVariableNode(
                    old.name,
                    old.desc,
                    old.signature,
                    labelCloner.get(old.start),
                    labelCloner.get(old.end),
                    remapLVT(old.index)
                )).collect(Collectors.toList());
            }

            implMethod.localVariables.add(
                new LocalVariableNode("state", Type.INT_TYPE.getDescriptor(), null, start, end, LVT_STATE)
            );
            genLocals(start, end, implMethod.localVariables);
        });
    }

    private void codegenConstructor() {
        final var output = implClassCons.instructions;

        output.add(new VarInsnNode(Opcodes.ALOAD, LVT_THIS));
        output.add(new InsnNode(Opcodes.DUP));
        output.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, implClass.superName, "<init>", "()V"));

        Util.forEachArgType(argTypes, (index, argType) -> {
            output.add(new InsnNode(Opcodes.DUP));
            // need to add 1 because 0 is used as "this"
            output.add(Util.loadLocal(1 + index, argType));
            output.add(argField(Opcodes.PUTFIELD, index, argType));
        });

        output.add(new InsnNode(Opcodes.POP));
        output.add(new InsnNode(Opcodes.RETURN));
    }

    private void codegenToString() {
        final var output = implClassToString.instructions;

        // TODO: use signature
        final var name = String.format(
            "%s[%s %s%s%s(%s)]",
            implClass.superName.substring(implClass.superName.lastIndexOf("/") + 1),
            Type.getReturnType(coMethod.desc).getClassName(),
            Type.getObjectType(coMethodOwner.name).getClassName(),
            (coMethod.access & Opcodes.ACC_STATIC) != 0 ? "." : "#",
            coMethod.name,
            Arrays.stream(Type.getArgumentTypes(coMethod.desc))
                .map(Type::getClassName)
                .collect(Collectors.joining(", "))
        );

        output.add(new LdcInsnNode(name));
        output.add(new InsnNode(Opcodes.ARETURN));
    }

    private void codegenCoMethod() {
        final var output = new InsnList();
        coMethod.instructions = output;
        coMethod.tryCatchBlocks = new ArrayList<>();

        output.add(new TypeInsnNode(Opcodes.NEW, implClass.name));
        output.add(new InsnNode(Opcodes.DUP));

        Util.forEachArgType(argTypes, (index, argType) -> coMethod.instructions.add(Util.loadLocal(index, argType)));

        output.add(Util.invokeInstr(Opcodes.INVOKESPECIAL, implClass, implClassCons));
        output.add(new InsnNode(Opcodes.ARETURN));
        coMethod.localVariables.clear();
    }

    private void codegenFields() {
        final var access = Opcodes.ACC_PRIVATE;
        final var fields = implClass.fields;

        Util.forEachArgType(argTypes, (index, argType) -> {
            fields.add(new FieldNode(access, "arg" + index, argType.getDescriptor(), null, null));
        });

        fieldAllocator.codegen(fields, access);
    }

    public final ClassNode generate() {
        genImplMethod();
        codegenConstructor();
        codegenCoMethod();
        codegenFields();
        codegenToString();

        if (Boolean.getBoolean("coro.debug")) {
            implClass.accept(new TraceClassVisitor(new PrintWriter(System.out)));
        }

        return implClass;
    }

    protected abstract void genReportSuspend(InsnList output, MethodInsnNode originalMethod);

    protected abstract void genReportReturn(InsnList output, MethodInsnNode methodInstr);

    protected abstract void genCheckException(InsnList output);

    protected abstract void genResume(InsnList output);

    protected abstract void genLocals(LabelNode beginLabel, LabelNode endLabel, List<LocalVariableNode> lvt);

    protected abstract void handleCoMethod(InsnList output, MethodInsnNode methodInstr);
}
