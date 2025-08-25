package com.floweytf.coro.ap.codegen;

import com.floweytf.coro.ap.util.Util;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
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
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.TraceClassVisitor;

import static com.floweytf.coro.ap.Constants.AWAIT_KW;
import static com.floweytf.coro.ap.Constants.BASIC_TASK_CLASS_BIN;
import static com.floweytf.coro.ap.Constants.BASIC_TASK_COMPLETE_ERROR;
import static com.floweytf.coro.ap.Constants.BASIC_TASK_COMPLETE_SUCCESS;
import static com.floweytf.coro.ap.Constants.BASIC_TASK_GET_EXECUTOR;
import static com.floweytf.coro.ap.Constants.BASIC_TASK_RUN;
import static com.floweytf.coro.ap.Constants.BASIC_TASK_SUSPEND_HELPER;
import static com.floweytf.coro.ap.Constants.CLASS_TYPE_BIN;
import static com.floweytf.coro.ap.Constants.COROUTINE_KW;
import static com.floweytf.coro.ap.Constants.CORO_METADATA_CLASS_BIN;
import static com.floweytf.coro.ap.Constants.CORO_METADATA_CLASS_CTOR;
import static com.floweytf.coro.ap.Constants.CORO_METADATA_CLASS_DESC;
import static com.floweytf.coro.ap.Constants.CO_CLASS_BIN;
import static com.floweytf.coro.ap.Constants.CURRENT_EXECUTOR_KW;
import static com.floweytf.coro.ap.Constants.OBJECT_CLASS_BIN;
import static com.floweytf.coro.ap.Constants.OBJECT_TYPE;
import static com.floweytf.coro.ap.Constants.RET_KW;
import static com.floweytf.coro.ap.Constants.THROWABLE_CLASS_BIN;
import static com.floweytf.coro.ap.Constants.THROWABLE_TYPE;

public class MethodTransformer {
    private static final int LVT_THIS = 0;
    private static final int LVT_STATE = 1;
    private static final int LVT_IS_EXCEPTION = LVT_STATE + 1;
    private static final int LVT_RES_VAL = LVT_STATE + 2;
    private static final int LVT_SCRATCH_SMALL = LVT_STATE + 3;
    private static final int LVT_OFFSET = LVT_STATE + 4;

    private static boolean isCoMethod(final AbstractInsnNode node) {
        if (!(node instanceof final MethodInsnNode methodInsnNode)) {
            return false;
        }

        return node.getOpcode() == Opcodes.INVOKESTATIC && methodInsnNode.owner.equals(CO_CLASS_BIN);
    }

    private final ClassNode coMethodOwner;
    private final MethodNode coMethod;

    private final ClassNode implClass;
    private final MethodNode implClassCons;
    private final MethodNode implClassStaticCons;
    private final MethodNode implMethod;
    private final MethodNode implSuspendPointMetadataGetter;

    private final FieldNode suspendPointMetadataField;

    private final List<Type> argTypes;
    private final List<LabelNode> resumeLabels = new ArrayList<>();
    private final AnalyzerAdapter analyzer;
    private final FieldAllocator fieldAllocator = new FieldAllocator();

    private final Object[] initialLVTTypes;

    private final IntList suspendPointLines = new IntArrayList();

    /**
     * A simple method transformer capable of handling coroutine methods.
     *
     * @param methodOwner The owner of the method.
     * @param coMethod    The method to transform.
     * @param id          An unique identifier.
     */
    public MethodTransformer(final ClassNode methodOwner, final MethodNode coMethod, final int id) {
        this.coMethodOwner = methodOwner;
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
        this.implClassStaticCons = new MethodNode(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "<clinit>",
            "()V",
            null,
            null
        );
        this.implMethod = BASIC_TASK_RUN.node(Opcodes.ACC_PROTECTED);
        this.implSuspendPointMetadataGetter = new MethodNode(
            Opcodes.ACC_PROTECTED,
            "getMetadata",
            "()" + CORO_METADATA_CLASS_DESC,
            null,
            null
        );

        this.suspendPointMetadataField = new FieldNode(
            Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
            "METADATA",
            CORO_METADATA_CLASS_DESC,
            null,
            null
        );

        // set up necessary attributes to recognize our generated class as an inner class
        this.implClass.access = Opcodes.ACC_SUPER | Opcodes.ACC_SYNTHETIC;
        this.implClass.name = String.format("%s$%s$Coro$%d", methodOwner.name, coMethod.name, id);
        this.implClass.outerClass = methodOwner.name;
        this.implClass.superName = BASIC_TASK_CLASS_BIN;
        this.implClass.version = methodOwner.version;
        this.implClass.methods.add(implMethod);
        this.implClass.methods.add(implClassCons);
        this.implClass.methods.add(implClassStaticCons);
        this.implClass.methods.add(implSuspendPointMetadataGetter);
        this.implClass.fields.add(suspendPointMetadataField);
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

        this.initialLVTTypes = new Object[1 + BASIC_TASK_RUN.arguments().length];

        initialLVTTypes[0] = implClass.name;
        for (int i = 0; i < BASIC_TASK_RUN.arguments().length; i++) {
            initialLVTTypes[i + 1] = Util.typeToFrameType(BASIC_TASK_RUN.arguments()[i]);
        }
    }

    private FrameNode createNode(final Object... stack) {
        return new FrameNode(Opcodes.F_NEW, initialLVTTypes.length, initialLVTTypes, stack.length, stack);
    }

    private int remapLVT(final int lvt) {
        return lvt + LVT_OFFSET;
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
                throw new AssertionError("uninitialize type in LVT not supported");
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
            output.add(new VarInsnNode(Opcodes.ASTORE, LVT_SCRATCH_SMALL));
            Util.forEachStack(frame, 1, (arg) -> {
                if (arg instanceof Util.StackEntry.Null) {
                    output.add(new InsnNode(Opcodes.POP));
                    resumeReversed.add(new InsnNode(Opcodes.ACONST_NULL));
                } else if (arg instanceof final Util.StackEntry.Uninitialized uninitialized) {
                    for (int i = 0; i < uninitialized.count(); i++) {
                        output.add(new InsnNode(Opcodes.POP));
                    }

                    for (int i = 0; i < uninitialized.count() - 1; i++) {
                        resumeReversed.add(new InsnNode(Opcodes.DUP));
                    }

                    resumeReversed.add(new TypeInsnNode(Opcodes.NEW, uninitialized.type().getInternalName()));
                } else if (arg instanceof final Util.StackEntry.Regular regular) {
                    final var fieldId = fieldAllocator.getOrAllocateFieldId(regular.type(), allocMap);
                    output.add(new VarInsnNode(Opcodes.ALOAD, LVT_THIS));
                    if (regular.type().getSize() == 2) {
                        output.add(new InsnNode(Opcodes.DUP_X2));
                    } else {
                        output.add(new InsnNode(Opcodes.DUP_X1));
                    }
                    output.add(new InsnNode(Opcodes.POP));
                    output.add(scratchField(Opcodes.PUTFIELD, fieldId, regular.type()));

                    // reverse order
                    resumeReversed.add(scratchField(Opcodes.GETFIELD, fieldId, regular.type()));
                    resumeReversed.add(new VarInsnNode(Opcodes.ALOAD, LVT_THIS));
                }
            });

            output.add(new VarInsnNode(Opcodes.ALOAD, LVT_SCRATCH_SMALL));
        }

        return () -> {
            for (var i = resumeReversed.size() - 1; i >= 0; i--) {
                output.add(resumeReversed.get(i));
            }
        };
    }

    private void handleSuspendPointMetadata(final String name) {
        final var argStart = name.indexOf("@");

        if (argStart == -1) {
            suspendPointLines.add(-1);
        }

        suspendPointLines.add(Integer.parseInt(name.substring(argStart + 1)));
    }

    private void genSuspendPoint(final MethodInsnNode node) {
        final var output = implMethod.instructions;
        final var allocMap = new Object2IntArrayMap<Type>();
        final var resumeLabel = new LabelNode();

        final var resumeLocal = suspendSaveLocals(analyzer, allocMap);
        final var resumeStack = suspendSaveStack(analyzer, allocMap);

        final var suspendPointId = resumeLabels.size();

        output.add(new VarInsnNode(Opcodes.ALOAD, LVT_THIS));
        output.add(new LdcInsnNode(suspendPointId));
        genReportSuspend(output);
        output.add(new InsnNode(Opcodes.RETURN));

        handleSuspendPointMetadata(node.name);

        output.add(resumeLabel);

        output.add(createNode());
        genCheckException(output);

        resumeLocal.run();
        resumeStack.run();

        genResume(output);

        resumeLabels.add(resumeLabel);
    }

    private void codegenImplMethod() {
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
                final var locals = new Object[LVT_OFFSET + frameNode.local.size()];
                final var stack = new Object[frameNode.stack.size()];

                System.arraycopy(initialLVTTypes, 0, locals, 0, initialLVTTypes.length);
                Arrays.fill(locals, initialLVTTypes.length, LVT_OFFSET, Opcodes.TOP);

                for (int off = 0; off < frameNode.local.size(); off++) {
                    locals[LVT_OFFSET + off] = Util.cloneFrameType(frameNode.local.get(off), labelCloner);
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
            output.add(BASIC_TASK_COMPLETE_ERROR.instr(Opcodes.INVOKESTATIC));
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

    private void codegenStaticConstructor() {
        final var output = implClassStaticCons.instructions;

        output.add(new TypeInsnNode(Opcodes.NEW, CORO_METADATA_CLASS_BIN));
        output.add(new InsnNode(Opcodes.DUP));

        output.add(new LdcInsnNode(Type.getObjectType(coMethodOwner.name)));
        output.add(new LdcInsnNode(coMethod.access));
        output.add(new LdcInsnNode(coMethod.name));

        final var desc = Type.getArgumentTypes(coMethod.desc);

        output.add(new LdcInsnNode(desc.length));
        output.add(new TypeInsnNode(Opcodes.ANEWARRAY, CLASS_TYPE_BIN));

        for (int i = 0; i < desc.length; i++) {
            output.add(new InsnNode(Opcodes.DUP));
            output.add(new LdcInsnNode(i));
            output.add(Util.ldc(desc[i]));
            output.add(new InsnNode(Opcodes.AASTORE));
        }

        output.add(Util.ldc(coMethodOwner.sourceFile));

        output.add(new LdcInsnNode(suspendPointLines.size()));
        output.add(new IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_INT));

        for (int i = 0; i < suspendPointLines.size(); i++) {
            output.add(new InsnNode(Opcodes.DUP));
            output.add(new LdcInsnNode(i));
            output.add(new LdcInsnNode(suspendPointLines.getInt(i)));
            output.add(new InsnNode(Opcodes.IASTORE));
        }

        output.add(CORO_METADATA_CLASS_CTOR.instr(Opcodes.INVOKESPECIAL));

        output.add(new FieldInsnNode(
            Opcodes.PUTSTATIC,
            implClass.name,
            suspendPointMetadataField.name,
            suspendPointMetadataField.desc
        ));

        output.add(new InsnNode(Opcodes.RETURN));
    }

    private void codegenSuspendPointMetadataGetter() {
        final var output = implSuspendPointMetadataGetter.instructions;

        output.add(new FieldInsnNode(
            Opcodes.GETSTATIC,
            implClass.name,
            suspendPointMetadataField.name,
            suspendPointMetadataField.desc
        ));
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

        Util.forEachArgType(argTypes, (index, argType) ->
            fields.add(new FieldNode(access, "arg" + index, argType.getDescriptor(), null, null)));

        fieldAllocator.generateFields(fields, access);
    }

    public final ClassNode generate() {
        codegenImplMethod();
        codegenConstructor();
        codegenStaticConstructor();
        codegenCoMethod();
        codegenFields();
        codegenSuspendPointMetadataGetter();

        if (Boolean.getBoolean("coro.debug")) {
            implClass.accept(new CheckClassAdapter(new TraceClassVisitor(new PrintWriter(System.out))));
        }

        return implClass;
    }

    private void genReportSuspend(final InsnList output) {
        output.add(BASIC_TASK_SUSPEND_HELPER.instr(Opcodes.INVOKESTATIC));
    }

    private void genReportReturn(final InsnList output, final MethodInsnNode methodInstr) {
        if (Type.getArgumentCount(methodInstr.desc) == 0) {
            output.add(new InsnNode(Opcodes.ACONST_NULL));
        }

        output.add(new VarInsnNode(Opcodes.ALOAD, LVT_THIS));
        output.add(BASIC_TASK_COMPLETE_SUCCESS.instr(Opcodes.INVOKESTATIC));
    }

    private void genCheckException(final InsnList output) {
        final var exit = new LabelNode();
        output.add(new VarInsnNode(Opcodes.ALOAD, LVT_RES_VAL));
        output.add(new VarInsnNode(Opcodes.ILOAD, LVT_IS_EXCEPTION));
        output.add(new JumpInsnNode(Opcodes.IFEQ, exit));
        output.add(new TypeInsnNode(Opcodes.CHECKCAST, THROWABLE_TYPE.getInternalName()));
        output.add(new InsnNode(Opcodes.ATHROW));
        output.add(exit);
        output.add(createNode(OBJECT_CLASS_BIN));
        output.add(new InsnNode(Opcodes.POP));
    }

    private void genResume(final InsnList output) {
        output.add(new VarInsnNode(Opcodes.ALOAD, LVT_RES_VAL));
    }

    private void genLocals(final LabelNode begin, final LabelNode end, final List<LocalVariableNode> lvt) {
        lvt.add(new LocalVariableNode("isEx", Type.BOOLEAN_TYPE.getDescriptor(), null, begin, end, LVT_IS_EXCEPTION));
        lvt.add(new LocalVariableNode("res", OBJECT_TYPE.getDescriptor(), null, begin, end, LVT_RES_VAL));
    }

    private void handleCoMethod(final InsnList output, final MethodInsnNode methodInstr) {
        if (methodInstr.name.startsWith(AWAIT_KW)) {
            genSuspendPoint(methodInstr);
        } else if (methodInstr.name.equals(CURRENT_EXECUTOR_KW)) {
            output.add(new VarInsnNode(Opcodes.ALOAD, LVT_THIS));
            output.add(BASIC_TASK_GET_EXECUTOR.instr(Opcodes.INVOKEVIRTUAL));
        } else if (!methodInstr.name.equals(COROUTINE_KW)) { // pass on COROUTINE_KW b/c it just returns id
            throw new AssertionError("illegal Co.<method> " + methodInstr.name);
        }
    }
}
