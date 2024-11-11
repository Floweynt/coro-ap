package com.floweytf.coro.ap.pass;

import com.floweytf.coro.ap.Constants;
import static com.floweytf.coro.ap.Constants.AWAIT_KW;
import static com.floweytf.coro.ap.Constants.BASIC_TASK_CLASS_BIN;
import static com.floweytf.coro.ap.Constants.BASIC_TASK_CLASS_COMPLETE_ERROR;
import static com.floweytf.coro.ap.Constants.BASIC_TASK_CLASS_COMPLETE_RUN;
import static com.floweytf.coro.ap.Constants.BASIC_TASK_CLASS_COMPLETE_SUCCESS;
import static com.floweytf.coro.ap.Constants.BASIC_TASK_CLASS_CONSTRUCTOR;
import static com.floweytf.coro.ap.Constants.BASIC_TASK_CLASS_GET_EXECUTOR;
import static com.floweytf.coro.ap.Constants.BASIC_TASK_CLASS_SUSPEND;
import static com.floweytf.coro.ap.Constants.CO_CLASS_BIN;
import static com.floweytf.coro.ap.Constants.CURRENT_EXECUTOR_KW;
import static com.floweytf.coro.ap.Constants.RET_KW;
import static com.floweytf.coro.ap.Constants.THROWABLE_CLASS_BIN;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import static org.objectweb.asm.Opcodes.*;
import org.objectweb.asm.Type;
import static org.objectweb.asm.Type.ARRAY;
import static org.objectweb.asm.Type.BOOLEAN;
import static org.objectweb.asm.Type.BYTE;
import static org.objectweb.asm.Type.CHAR;
import static org.objectweb.asm.Type.DOUBLE;
import static org.objectweb.asm.Type.FLOAT;
import static org.objectweb.asm.Type.INT;
import static org.objectweb.asm.Type.LONG;
import static org.objectweb.asm.Type.OBJECT;
import static org.objectweb.asm.Type.SHORT;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
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
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.util.TraceClassVisitor;

public class MethodAnalysisContext {
    private static final int LVT_THIS = 0;
    private static final int LVT_STATE = 1;
    private static final int LVT_IS_EXCEPTION = 2;
    private static final int LVT_RES_VAL = 3;
    private static final int LVT_SCRATCH_LONG = 4; // 5
    private static final int LVT_SCRATCH_SMALL = 6;
    private static final int LVT_ARG_SIZE = 8;

    private final ClassNode coMethodOwner;
    private final MethodNode coMethod;

    private final String taskImplClassName;
    private final ClassNode taskImplClass;
    private final String consDesc;
    private final MethodNode taskImplClassCons;
    private final MethodNode taskImplRun;

    private final List<Type> argTypes;

    private final List<LabelNode> resumeLabels = new ArrayList<>();

    private final Map<Type, IntList> localStoragePool = new Object2ObjectOpenHashMap<>();
    private int allocFieldId = 0;

    private MethodAnalysisContext(ClassNode owner, MethodNode coMethod, int id) {
        this.coMethodOwner = owner;
        this.taskImplClassName = String.format("%s$%s$Coro$%d", owner.name, coMethod.name, id);

        this.argTypes = new ArrayList<>(Arrays.asList(Type.getMethodType(coMethod.desc).getArgumentTypes()));

        if (!isStatic(coMethod)) {
            argTypes.add(0, Type.getType("L" + owner.name + ";"));
        }
        this.consDesc = Type.getMethodType(Type.VOID_TYPE, argTypes.toArray(Type[]::new)).getDescriptor();

        this.coMethod = coMethod;
        this.taskImplClass = new ClassNode();
        this.taskImplClassCons = new MethodNode(0, "<init>", consDesc, null, null);
        this.taskImplRun = BASIC_TASK_CLASS_COMPLETE_RUN.node(ACC_PROTECTED);

        // setup attributes & such
        taskImplClass.access = ACC_SUPER | ACC_SYNTHETIC;
        taskImplClass.name = taskImplClassName;
        taskImplClass.outerClass = coMethodOwner.name;
        taskImplClass.superName = BASIC_TASK_CLASS_BIN;
        taskImplClass.version = coMethodOwner.version;
        taskImplClass.methods.add(taskImplRun);
        taskImplClass.methods.add(taskImplClassCons);
        taskImplClass.sourceFile = coMethodOwner.sourceFile;
        taskImplClass.nestHostClass = coMethodOwner.name;
        taskImplClass.outerMethod = coMethod.name;
        taskImplClass.outerMethodDesc = coMethod.desc;
        taskImplClass.innerClasses.add(new InnerClassNode(taskImplClassName, null, null, 0));
        coMethodOwner.innerClasses.add(new InnerClassNode(taskImplClassName, null, null, 0));
        if (coMethodOwner.nestMembers == null) {
            coMethodOwner.nestMembers = new ArrayList<>();
        }
        coMethodOwner.nestMembers.add(taskImplClassName);
    }

    private static Frame<BasicValue>[] analyze(ClassNode owner, MethodNode method) {
        try {
            return new Analyzer<>(new BasicInterpreter()).analyze(owner.name, method);
        } catch (AnalyzerException e) {
            throw new RuntimeException(e);
        }
    }

    private static int remapLVT(int lvt) {
        return lvt + LVT_ARG_SIZE;
    }

    private static boolean isCoMethod(AbstractInsnNode node) {
        if (!(node instanceof MethodInsnNode methodInsnNode)) {
            return false;
        }

        return node.getOpcode() == INVOKESTATIC && methodInsnNode.owner.equals(CO_CLASS_BIN);
    }

    public static boolean isStatic(MethodNode node) {
        return (node.access & ACC_STATIC) != 0;
    }

    private int getOrAllocateFieldId(Type type, Object2IntMap<Type> map) {
        final var index = map.getOrDefault(type, 0);
        final var fields = localStoragePool.computeIfAbsent(type, ignored -> new IntArrayList());

        if (fields.size() <= index) {
            fields.add(allocFieldId++);
        }

        map.put(type, index + 1);
        return fields.getInt(index);
    }

    private FieldInsnNode scratchField(int opc, int id, BasicValue value) {
        return new FieldInsnNode(opc, taskImplClassName, "l" + id, value.getType().getDescriptor());
    }

    private FieldInsnNode argField(int opc, int id, Type type) {
        return new FieldInsnNode(opc, taskImplClassName, "arg" + id, type.getDescriptor());
    }

    private VarInsnNode loadLocal(int id, Type type) {
        return new VarInsnNode(
            switch (type.getSort()) {
                case BOOLEAN, CHAR, BYTE, SHORT, INT -> ILOAD;
                case FLOAT -> FLOAD;
                case LONG -> LLOAD;
                case DOUBLE -> DLOAD;
                case ARRAY, OBJECT -> ALOAD;
                default -> throw new AssertionError();
            },
            id
        );
    }

    private VarInsnNode loadLocal(int id, BasicValue value) {
        return loadLocal(id, value.getType());
    }

    private VarInsnNode storeLocal(int id, Type type) {
        return new VarInsnNode(
            switch (type.getSort()) {
                case BOOLEAN, CHAR, BYTE, SHORT, INT -> ISTORE;
                case FLOAT -> FSTORE;
                case LONG -> LSTORE;
                case DOUBLE -> DSTORE;
                case ARRAY, OBJECT -> ASTORE;
                default -> throw new AssertionError();
            },
            id
        );
    }

    private VarInsnNode storeLocal(int id, BasicValue value) {
        return storeLocal(id, value.getType());
    }

    private void foreachArgTypes(BiConsumer<Integer, Type> handler) {
        int index = 0;
        for (Type argType : argTypes) {
            handler.accept(index, argType);
            index += argType.getSize();
        }
    }

    private void withMethodBody(InsnList instructions, BiConsumer<LabelNode, LabelNode> handler) {
        final var startLabel = new LabelNode();
        final var endLabel = new LabelNode();

        instructions.insert(startLabel);
        instructions.add(endLabel);

        handler.accept(startLabel, endLabel);
    }

    private Runnable codegenSuspendLocals(Frame<BasicValue> frame, Object2IntMap<Type> allocMap) {
        final var output = taskImplRun.instructions;

        var localIndex = 0;
        final var resumeInstr = new InsnList();

        output.add(new VarInsnNode(ALOAD, LVT_THIS));
        resumeInstr.add(new VarInsnNode(ALOAD, LVT_THIS));
        while (localIndex < frame.getLocals()) {
            final var local = frame.getLocal(localIndex);
            if (local.getType() != null) {
                final var fieldNum = getOrAllocateFieldId(local.getType(), allocMap);

                output.add(new InsnNode(DUP));
                output.add(loadLocal(remapLVT(localIndex), local));
                output.add(scratchField(PUTFIELD, fieldNum, local));

                resumeInstr.add(new InsnNode(DUP));
                resumeInstr.add(scratchField(GETFIELD, fieldNum, local));
                resumeInstr.add(storeLocal(remapLVT(localIndex), local));
            }

            localIndex += local.getSize();
        }
        output.add(new InsnNode(POP));
        resumeInstr.add(new InsnNode(POP));

        return () -> output.add(resumeInstr);
    }

    private Runnable codegenSuspendStack(Frame<BasicValue> frame, Object2IntMap<Type> allocMap) {
        final var resumeReversed = new ArrayList<AbstractInsnNode>();
        final var output = taskImplRun.instructions;

        if (frame.getStackSize() > 1) {
            output.add(new VarInsnNode(ASTORE, LVT_SCRATCH_SMALL));
            for (int j = frame.getStackSize() - 2; j >= 0; j--) {
                final var stack = frame.getStack(j);
                final var fieldId = getOrAllocateFieldId(stack.getType(), allocMap);
                output.add(new VarInsnNode(ASTORE, LVT_SCRATCH_LONG));
                output.add(new VarInsnNode(ALOAD, LVT_THIS));
                output.add(new VarInsnNode(ALOAD, LVT_SCRATCH_LONG));
                output.add(scratchField(PUTFIELD, fieldId, stack));

                // reverse order
                output.add(scratchField(GETFIELD, fieldId, stack));
                resumeReversed.add(new VarInsnNode(ALOAD, LVT_THIS));
            }
            output.add(new VarInsnNode(ALOAD, LVT_SCRATCH_SMALL));
        }

        return () -> {
            for (int i = resumeReversed.size() - 1; i >= 0; i--) {
                output.add(resumeReversed.get(i));
            }
        };
    }

    private void codegenSuspend(InsnList output, Frame<BasicValue> frame) {
        final var allocMap = new Object2IntArrayMap<Type>();
        final var resumeLabel = new LabelNode();

        final var resumeLocal = codegenSuspendLocals(frame, allocMap);
        final var resumeStack = codegenSuspendStack(frame, allocMap);

        output.add(new VarInsnNode(ALOAD, LVT_THIS));
        output.add(new LdcInsnNode(resumeLabels.size()));
        output.add(BASIC_TASK_CLASS_SUSPEND.instr(INVOKESTATIC));
        output.add(new InsnNode(RETURN));

        output.add(resumeLabel);
        resumeLocal.run();
        resumeStack.run();
        final var checkEhExit = new LabelNode();
        output.add(new VarInsnNode(ALOAD, LVT_RES_VAL));

        // handle exception case
        output.add(new VarInsnNode(ILOAD, LVT_IS_EXCEPTION));
        output.add(new JumpInsnNode(IFEQ, checkEhExit));
        output.add(new TypeInsnNode(CHECKCAST, THROWABLE_CLASS_BIN));
        output.add(new InsnNode(ATHROW));
        output.add(checkEhExit);

        resumeLabels.add(resumeLabel);
    }

    private void codegenRunImplCopyBody() {
        final var instructions = coMethod.instructions.toArray();
        final Frame<BasicValue>[] frames = analyze(coMethodOwner, coMethod);
        final var output = taskImplRun.instructions;
        final var labelCloner = new Object2ObjectOpenHashMap<LabelNode, LabelNode>() {
            @Override
            public LabelNode get(Object k) {
                var res = super.get(k);
                if (res == null) {
                    res = new LabelNode();
                    put((LabelNode) k, res);
                }
                return res;
            }
        };

        // process main instruction
        for (int i = 0; i < instructions.length; i++) {
            final var instruction = instructions[i];
            final var frame = frames[i];

            // we need to remap the LVT, since we promote the args of the coroutine method to fields
            // we also need to remap individual lvt entries, since it maybe shifted because run() takes 4 (this + 3)
            // args
            if (instruction instanceof VarInsnNode varInstNode) {
                output.add(new VarInsnNode(varInstNode.getOpcode(), remapLVT(varInstNode.var)));
            } else if (instruction instanceof IincInsnNode iincInsnNode) {
                output.add(new IincInsnNode(remapLVT(iincInsnNode.var), iincInsnNode.incr));
            } else if (isCoMethod(instruction)) {
                final var methodInstr = (MethodInsnNode) instruction;
                switch (methodInstr.name) {
                case AWAIT_KW -> codegenSuspend(output, frame);
                case RET_KW -> {
                    if (i + 1 >= instructions.length || instructions[i + 1].getOpcode() != ARETURN) {
                        throw new AssertionError();
                    }

                    if (Type.getArgumentCount(methodInstr.desc) == 0) {
                        output.add(new VarInsnNode(ALOAD, LVT_THIS));
                        output.add(new InsnNode(ACONST_NULL));
                    } else {
                        output.add(new VarInsnNode(ALOAD, LVT_THIS));
                        output.add(new InsnNode(SWAP));
                    }

                    output.add(BASIC_TASK_CLASS_COMPLETE_SUCCESS.instr(INVOKEVIRTUAL));

                    output.add(new InsnNode(RETURN));
                    i++;
                }
                case CURRENT_EXECUTOR_KW -> {
                    output.add(new VarInsnNode(ALOAD, LVT_THIS));
                    output.add(BASIC_TASK_CLASS_GET_EXECUTOR.instr(INVOKEVIRTUAL));
                }
                default -> throw new IllegalStateException();
                }
            } else {
                // copy over the instruction
                output.add(instruction.clone(labelCloner));
            }
        }

        // TODO: clone annotations
        coMethod.tryCatchBlocks.stream().map(block -> new TryCatchBlockNode(
            labelCloner.get(block.start),
            labelCloner.get(block.end),
            labelCloner.get(block.handler),
            block.type
        )).forEach(taskImplRun.tryCatchBlocks::add);
        coMethod.tryCatchBlocks = new ArrayList<>();
    }

    private void codegenRunImplEhWrapper(LabelNode beginLabel, LabelNode endLabel) {
        final var output = taskImplRun.instructions;
        final var catcher = new LabelNode();

        output.add(catcher);

        output.add(new VarInsnNode(ALOAD, LVT_THIS));
        output.add(new InsnNode(SWAP));
        output.add(BASIC_TASK_CLASS_COMPLETE_ERROR.instr(INVOKEVIRTUAL));
        output.add(new InsnNode(RETURN));

        taskImplRun.tryCatchBlocks.add(new TryCatchBlockNode(beginLabel, endLabel, catcher, THROWABLE_CLASS_BIN));
    }

    private void codegenRunImplCopyLVT(LabelNode beginLabel, LabelNode endLabel) {
        if (coMethod.localVariables == null) {
            taskImplRun.localVariables = new ArrayList<>();
        } else {
            taskImplRun.localVariables = coMethod.localVariables.stream()
                .map(old -> new LocalVariableNode(
                    old.name,
                    old.desc,
                    old.signature,
                    old.start,
                    old.end,
                    remapLVT(old.index)
                ))
                .collect(Collectors.toList());
        }

        taskImplRun.localVariables.add(new LocalVariableNode(
            "state",
            Type.INT_TYPE.getDescriptor(),
            null,
            beginLabel,
            endLabel,
            LVT_STATE
        ));

        taskImplRun.localVariables.add(new LocalVariableNode(
            "isException",
            Type.BOOLEAN_TYPE.getDescriptor(),
            null,
            beginLabel,
            endLabel,
            LVT_IS_EXCEPTION
        ));

        taskImplRun.localVariables.add(new LocalVariableNode(
            "resVal",
            "L" + Constants.OBJECT_CLASS_BIN + ";",
            null,
            beginLabel,
            endLabel,
            LVT_RES_VAL
        ));
    }

    private void codegenRunImpl() {
        final var output = taskImplRun.instructions;

        // generate preamble
        final var entryLabel = new LabelNode();
        resumeLabels.add(entryLabel);
        output.add(entryLabel);

        output.add(new VarInsnNode(ALOAD, LVT_THIS));

        foreachArgTypes((index, argType) -> {
            output.add(new InsnNode(DUP));
            output.add(argField(GETFIELD, index, argType));
            output.add(storeLocal(remapLVT(index), argType));
        });

        output.add(new InsnNode(POP));

        // copy body
        codegenRunImplCopyBody();

        // generate the switch table
        output.insert(new TableSwitchInsnNode(
            0,
            resumeLabels.size() - 1,
            entryLabel,
            resumeLabels.toArray(LabelNode[]::new)
        ));
        output.insert(new VarInsnNode(ILOAD, LVT_STATE));

        withMethodBody(output, this::codegenRunImplCopyLVT);
        withMethodBody(output, this::codegenRunImplEhWrapper);
    }

    private void codegenConstructor() {
        final var output = taskImplClassCons.instructions;

        output.add(new VarInsnNode(ALOAD, LVT_THIS));
        output.add(new InsnNode(DUP));
        output.add(BASIC_TASK_CLASS_CONSTRUCTOR.instr(INVOKESPECIAL));

        foreachArgTypes((index, argType) -> {
            output.add(new InsnNode(DUP));
            // need to add 1 because 0 is used as "this"
            output.add(loadLocal(1 + index, argType));
            output.add(argField(PUTFIELD, index, argType));
        });

        output.add(new InsnNode(POP));
        output.add(new InsnNode(RETURN));
    }

    private void codegenCoMethod() {
        final var output = new InsnList();
        coMethod.instructions = output;

        output.add(new TypeInsnNode(NEW, taskImplClassName));
        output.add(new InsnNode(DUP));

        foreachArgTypes((index, argType) -> coMethod.instructions.add(loadLocal(index, argType)));

        output.add(new MethodInsnNode(INVOKESPECIAL, taskImplClassName, "<init>", consDesc));
        output.add(new InsnNode(ARETURN));
    }

    private void codegenFields() {
        final var access = ACC_PRIVATE;
        final var fields = taskImplClass.fields;

        foreachArgTypes((index, argType) -> fields.add(new FieldNode(
            access, "arg" + index, argType.getDescriptor(), null, null
        )));

        localStoragePool.forEach((type, integers) -> integers.forEach(i -> fields.add(new FieldNode(
            access, "l" + i, type.getDescriptor(), null, null
        ))));
    }

    private ClassNode codegen() {
        codegenRunImpl();
        codegenConstructor();
        codegenCoMethod();
        codegenFields();
        return taskImplClass;
    }

    /**
     * Generates the coroutine implementation class for a coroutine method.
     *
     * @param owner The owner of {@code node}
     * @param node  The coroutine method to process.
     * @param id    The id of the coroutine in this method. Only exists to ensure generated class names are unique.
     * @return The coroutine implementation class.
     */
    public static ClassNode generate(ClassNode owner, MethodNode node, int id) {
        final var output = new MethodAnalysisContext(owner, node, id).codegen();

        if (Boolean.getBoolean("coro.debug")) {
            TraceClassVisitor tcv = new TraceClassVisitor(new PrintWriter(System.out));
            output.accept(tcv);
        }

        return output;
    }
}
