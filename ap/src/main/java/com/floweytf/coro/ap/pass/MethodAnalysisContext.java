package com.floweytf.coro.ap.pass;

import com.floweytf.coro.ap.Constants;
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
import java.util.function.Consumer;
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
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.util.TraceClassVisitor;

/**
 * Information for analyzing and transforming a coroutine method.
 */
public class MethodAnalysisContext {
    private static final int LVT_THIS = 0;
    private static final int LVT_STATE = 1;
    private static final int LVT_IS_EXCEPTION = 2;
    private static final int LVT_RES_VAL = 3;
    private static final int LVT_SCRATCH_LONG = 4; // 5
    private static final int LVT_SCRATCH_SMALL = 6;
    private static final int LVT_ARG_SIZE = 8;

    private final MethodNode coMethod;
    private final int originalLvtArgSize;
    private final ClassNode owner;
    private final String generatedType;
    private final Type thisType;
    private final List<Type> argTypes;

    private final AbstractInsnNode[] instructions;
    private final Frame<BasicValue>[] frames;
    private final List<LabelNode> resumeLabels = new ArrayList<>();

    private final Map<Type, IntList> localStoragePool = new Object2ObjectOpenHashMap<>();
    private int allocFieldId = 0;

    private MethodAnalysisContext(ClassNode owner, MethodNode coMethod, String generatedType) {
        this.coMethod = coMethod;
        this.owner = owner;
        this.originalLvtArgSize = (Type.getArgumentsAndReturnSizes(coMethod.signature) >> 2) -
            (isStatic(coMethod) ? 1 : 0);

        this.instructions = coMethod.instructions.toArray();
        this.generatedType = generatedType;
        try {
            this.frames = new Analyzer<>(new BasicInterpreter()).analyze(owner.name, coMethod);
        } catch (AnalyzerException e) {
            throw new RuntimeException(e);
        }


        this.thisType = Type.getType("L" + owner.name + ";");
        this.argTypes = Arrays.asList(Type.getMethodType(coMethod.desc).getArgumentTypes());
        if (!isStatic(coMethod)) {
            argTypes.add(0, thisType);
        }
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

    private int remapLVT(int lvt) {
        return lvt + LVT_ARG_SIZE;
    }

    private static boolean isCoMethod(AbstractInsnNode node) {
        if (!(node instanceof MethodInsnNode methodInsnNode)) {
            return false;
        }

        return node.getOpcode() == INVOKESTATIC && methodInsnNode.owner.equals(Constants.CO_CLASS_BIN);
    }

    public static boolean isStatic(MethodNode node) {
        return (node.access & ACC_STATIC) != 0;
    }

    private FieldInsnNode scratchField(int opc, int id, BasicValue value) {
        return new FieldInsnNode(opc, generatedType, "l" + id, value.getType().getDescriptor());
    }

    private FieldInsnNode argField(int opc, int id, Type type) {
        return new FieldInsnNode(opc, generatedType, "arg" + id, type.getDescriptor());
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

    private Consumer<InsnList> codegenSuspendLocals(
        Frame<BasicValue> frame, Object2IntMap<Type> allocMap, InsnList outputInstr
    ) {
        if (originalLvtArgSize >= frame.getLocals()) {
            return ignored -> {
            };
        }

        var localIndex = 0;
        final var resumeInstr = new InsnList();

        outputInstr.add(new VarInsnNode(ALOAD, LVT_THIS));
        resumeInstr.add(new VarInsnNode(ALOAD, LVT_THIS));
        while (localIndex < frame.getLocals()) {
            final var local = frame.getLocal(localIndex);
            final var fieldNum = getOrAllocateFieldId(local.getType(), allocMap);

            outputInstr.add(new InsnNode(DUP));
            outputInstr.add(loadLocal(remapLVT(localIndex), local));
            outputInstr.add(scratchField(PUTFIELD, fieldNum, local));

            resumeInstr.add(new InsnNode(DUP));
            resumeInstr.add(scratchField(GETFIELD, fieldNum, local));
            resumeInstr.add(storeLocal(remapLVT(localIndex), local));

            localIndex += local.getSize();
        }
        outputInstr.add(new InsnNode(POP));
        resumeInstr.add(new InsnNode(POP));

        return output -> output.add(resumeInstr);
    }

    private Consumer<InsnList> codegenSuspendStack(
        Frame<BasicValue> frame, Object2IntMap<Type> allocMap, InsnList output
    ) {
        final var resumeReversed = new ArrayList<AbstractInsnNode>();

        if (frame.getStackSize() > 1) {
            output.add(new VarInsnNode(ASTORE, LVT_SCRATCH_SMALL));
            for (int j = frame.getStackSize() - 2; j >= 0; j--) {
                final var stack = frame.getStack(j);
                final var fieldId = getOrAllocateFieldId(stack.getType(), allocMap);
                output.add(new VarInsnNode(ASTORE, LVT_SCRATCH_LONG));
                output.add(new VarInsnNode(ALOAD, LVT_THIS));
                output.add(new VarInsnNode(ALOAD, LVT_SCRATCH_LONG));
                output.add(scratchField(PUTFIELD, fieldId, stack));

                // LOAD this
                // get field

                // reverse order because of stupid reasons
                output.add(scratchField(GETFIELD, fieldId, stack));
                resumeReversed.add(new VarInsnNode(ALOAD, LVT_THIS));
            }
            output.add(new VarInsnNode(ALOAD, LVT_SCRATCH_SMALL));
        }

        return insnList -> {
            for (int i = resumeReversed.size() - 1; i >= 0; i--) {
                insnList.add(resumeReversed.get(i));
            }
        };
    }

    private void codegenSuspend(InsnList output, Frame<BasicValue> frame) {
        final var allocMap = new Object2IntArrayMap<Type>();
        final var resumeLabel = new LabelNode();

        final var resumeLocal = codegenSuspendLocals(frame, allocMap, output);
        final var resumeStack = codegenSuspendStack(frame, allocMap, output);

        output.add(new VarInsnNode(ALOAD, LVT_THIS));
        output.add(new LdcInsnNode(resumeLabels.size()));
        output.add(new MethodInsnNode(
            INVOKESTATIC, Constants.BASIC_TASK_CLASS_BIN, Constants.BASIC_TASK_CLASS_SUSPEND,
            Constants.BASIC_TASK_CLASS_SUSPEND_SIG
        ));
        output.add(new InsnNode(RETURN));

        output.add(resumeLabel);
        resumeLocal.accept(output);
        resumeStack.accept(output);
        output.add(new VarInsnNode(ALOAD, LVT_RES_VAL));

        resumeLabels.add(resumeLabel);
    }

    private void codegen(InsnList outputInstr) {
        final var beginLabel = new LabelNode();
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

        resumeLabels.add(beginLabel);
        outputInstr.add(beginLabel);

        // copy args into locals
        outputInstr.add(new VarInsnNode(ALOAD, LVT_THIS));
        int index = 0;
        for (Type argType : argTypes) {
            outputInstr.add(new InsnNode(DUP));
            outputInstr.add(argField(GETFIELD, index, argType));
            outputInstr.add(storeLocal(remapLVT(index), argType));
            index += argType.getSize();
        }
        outputInstr.add(new InsnNode(POP));

        for (int i = 0; i < instructions.length; i++) {
            final var instruction = instructions[i];
            final var frame = frames[i];

            // we need to remap the LVT, since we promote the args of the coroutine method to fields
            // we also need to remap individual lvt entries, since it maybe shifted because run() takes 4 (this + 3)
            // args
            if (instruction instanceof VarInsnNode varInstNode) {
                outputInstr.add(new VarInsnNode(varInstNode.getOpcode(), remapLVT(varInstNode.var)));
            } else if (instruction instanceof IincInsnNode iincInsnNode) {
                outputInstr.add(new IincInsnNode(remapLVT(iincInsnNode.var), iincInsnNode.incr));
            } else if (isCoMethod(instruction)) {
                final var methodInstr = (MethodInsnNode) instruction;
                if (methodInstr.name.equals(Constants.AWAIT)) {
                    codegenSuspend(outputInstr, frame);
                } else if (methodInstr.name.equals(Constants.RET)) {
                    if (i + 1 >= instructions.length || instructions[i + 1].getOpcode() != ARETURN) {
                        throw new AssertionError();
                    }

                    if (Type.getArgumentCount(methodInstr.desc) == 0) {
                        outputInstr.add(new VarInsnNode(ALOAD, LVT_THIS));
                        outputInstr.add(new InsnNode(ACONST_NULL));
                    } else {
                        outputInstr.add(new VarInsnNode(ALOAD, LVT_THIS));
                        outputInstr.add(new InsnNode(SWAP));
                    }

                    outputInstr.add(new MethodInsnNode(
                        INVOKEVIRTUAL,
                        Constants.BASIC_TASK_CLASS_BIN,
                        Constants.BASIC_TASK_CLASS_COMPLETE_SUCCESS,
                        Constants.BASIC_TASK_CLASS_COMPLETE_SUCCESS_SIG
                    ));

                    outputInstr.add(new InsnNode(RETURN));
                    i++;
                }
            } else {
                // copy over the instruction
                outputInstr.add(instruction.clone(labelCloner));
            }
        }

        // reversed order
        outputInstr.insert(new TableSwitchInsnNode(
            0,
            resumeLabels.size() - 1,
            resumeLabels.get(0),
            resumeLabels.toArray(LabelNode[]::new)
        ));
        outputInstr.insert(new VarInsnNode(ILOAD, LVT_STATE));
    }

    private ClassNode generate() {
        final var ctorDesc = Type.getMethodType(
            Type.VOID_TYPE,
            argTypes.toArray(Type[]::new)
        ).getDescriptor();

        final var classNode = new ClassNode();
        final var constructor = new MethodNode(0, "<init>", ctorDesc, null, null);
        final var runImpl = new MethodNode(
            ACC_PROTECTED,
            Constants.BASIC_TASK_CLASS_COMPLETE_RUN,
            Constants.BASIC_TASK_CLASS_COMPLETE_RUN_SIG,
            null, null
        );

        // setup class node
        classNode.access = ACC_SUPER | ACC_SYNTHETIC;
        classNode.name = generatedType;
        classNode.outerClass = owner.name;
        classNode.superName = Constants.BASIC_TASK_CLASS_BIN;
        classNode.version = owner.version;
        classNode.methods.add(runImpl);
        classNode.methods.add(constructor);
        classNode.sourceFile = owner.sourceFile;
        classNode.outerMethod = runImpl.name;
        classNode.nestHostClass = owner.name;
        classNode.outerMethodDesc = runImpl.desc;
        classNode.innerClasses.add(new InnerClassNode(generatedType, null, null, 0));
        owner.innerClasses.add(new InnerClassNode(generatedType, null, null, 0));
        if(owner.nestMembers == null) {
            owner.nestMembers = new ArrayList<>();
        }
        owner.nestMembers.add(generatedType);

        // setup constructor preamble
        constructor.instructions.add(new VarInsnNode(ALOAD, LVT_THIS));
        constructor.instructions.add(new InsnNode(DUP));
        constructor.instructions.add(new MethodInsnNode(
            INVOKESPECIAL, Constants.BASIC_TASK_CLASS_BIN, "<init>", "()V"
        ));

        // clear and setup coroutine method preamble
        coMethod.instructions = new InsnList();
        coMethod.instructions.add(new TypeInsnNode(NEW, generatedType));
        coMethod.instructions.add(new InsnNode(DUP));

        // setup index
        int index = 0;
        for (Type argType : argTypes) {
            constructor.instructions.add(new InsnNode(DUP));
            // need to add 1 because 0 is used
            constructor.instructions.add(loadLocal(1 + index, argType));
            constructor.instructions.add(argField(PUTFIELD, index, argType));

            coMethod.instructions.add(loadLocal(index, argType));

            classNode.fields.add(new FieldNode(
                ACC_PRIVATE | ACC_SYNTHETIC, "arg" + index, argType.getDescriptor(), null, null
            ));
            index += argType.getSize();
        }

        // finish constructor
        constructor.instructions.add(new InsnNode(POP));
        constructor.instructions.add(new InsnNode(RETURN));

        // finish node methods
        coMethod.instructions.add(new MethodInsnNode(
            INVOKESPECIAL, generatedType, "<init>", ctorDesc
        ));
        coMethod.instructions.add(new InsnNode(ARETURN));

        // implement run method
        codegen(runImpl.instructions);

        // setup local storage pool
        localStoragePool.forEach((type, integers) -> integers.forEach(i -> classNode.fields.add(new FieldNode(
            ACC_PRIVATE | ACC_SYNTHETIC, "l" + i, type.getDescriptor(), null, null
        ))));

        return classNode;
    }

    /**
     * Generates the coroutine implementation class for a coroutine method.
     *
     * @param owner The owner of {@param node}
     * @param node  The coroutine method to process.
     * @param id    The id of the coroutine in this method. Only exists to ensure generated class names are unique.
     * @return The coroutine implementation class.
     */
    public static ClassNode generate(ClassNode owner, MethodNode node, int id) {
        final var outputName = String.format("%s$%s$Coro$%d", owner.name, node.name, id);
        final var output = new MethodAnalysisContext(owner, node, outputName).generate();

        if (Boolean.getBoolean("coro.debug")) {
            TraceClassVisitor tcv = new TraceClassVisitor(new PrintWriter(System.out));
            output.accept(tcv);
        }
        return output;
    }
}
