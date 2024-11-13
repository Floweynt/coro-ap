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
import com.floweytf.coro.ap.util.Util;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AnalyzerAdapter;
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
import org.objectweb.asm.util.TraceClassVisitor;

public class MethodTransformer {
    private static final int LVT_THIS = 0;
    private static final int LVT_STATE = 1;
    private static final int LVT_IS_EXCEPTION = 2;
    private static final int LVT_RES_VAL = 3;
    private static final int LVT_SCRATCH_SMALL = 4;
    private static final int LVT_ARG_SIZE = 5;

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

    private MethodTransformer(ClassNode coMethodOwner, MethodNode coMethod, int id) {
        this.coMethodOwner = coMethodOwner;
        this.taskImplClassName = String.format("%s$%s$Coro$%d", coMethodOwner.name, coMethod.name, id);
        this.argTypes = Util.getAllMethodArgs(coMethodOwner, coMethod);
        this.consDesc = Type.getMethodType(Type.VOID_TYPE, argTypes.toArray(Type[]::new)).getDescriptor();
        this.coMethod = coMethod;
        this.taskImplClass = new ClassNode();
        this.taskImplClassCons = new MethodNode(0, "<init>", consDesc, null, null);
        this.taskImplRun = BASIC_TASK_CLASS_COMPLETE_RUN.node(Opcodes.ACC_PROTECTED);

        // setup attributes & such
        this.taskImplClass.access = Opcodes.ACC_SUPER | Opcodes.ACC_SYNTHETIC;
        this.taskImplClass.name = taskImplClassName;
        this.taskImplClass.outerClass = this.coMethodOwner.name;
        this.taskImplClass.superName = BASIC_TASK_CLASS_BIN;
        this.taskImplClass.version = this.coMethodOwner.version;
        this.taskImplClass.methods.add(taskImplRun);
        this.taskImplClass.methods.add(taskImplClassCons);
        this.taskImplClass.sourceFile = this.coMethodOwner.sourceFile;
        this.taskImplClass.nestHostClass = this.coMethodOwner.name;
        this.taskImplClass.outerMethod = coMethod.name;
        this.taskImplClass.outerMethodDesc = coMethod.desc;
        this.taskImplClass.innerClasses.add(new InnerClassNode(taskImplClassName, null, null, 0));
        this.coMethodOwner.innerClasses.add(new InnerClassNode(taskImplClassName, null, null, 0));
        if (this.coMethodOwner.nestMembers == null) {
            this.coMethodOwner.nestMembers = new ArrayList<>();
        }
        this.coMethodOwner.nestMembers.add(taskImplClassName);
    }

    private static int remapLVT(int lvt) {
        return lvt + LVT_ARG_SIZE;
    }

    private static boolean isCoMethod(AbstractInsnNode node) {
        if (!(node instanceof MethodInsnNode methodInsnNode)) {
            return false;
        }

        return node.getOpcode() == Opcodes.INVOKESTATIC && methodInsnNode.owner.equals(CO_CLASS_BIN);
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

    private FieldInsnNode scratchField(int opc, int id, Type value) {
        return new FieldInsnNode(opc, taskImplClassName, "l" + id, value.getDescriptor());
    }

    private FieldInsnNode argField(int opc, int id, Type type) {
        return new FieldInsnNode(opc, taskImplClassName, "arg" + id, type.getDescriptor());
    }

    private Runnable codegenSuspendLocals(AnalyzerAdapter frame, Object2IntMap<Type> allocMap) {
        final var output = taskImplRun.instructions;
        final var resumeInstr = new InsnList();

        if (frame.locals.isEmpty()) {
            return () -> {
            };
        }

        output.add(new VarInsnNode(Opcodes.ALOAD, LVT_THIS));
        resumeInstr.add(new VarInsnNode(Opcodes.ALOAD, LVT_THIS));

        Util.forEachLocal(frame, (localIndex, type, isInit) -> {
            // TODO: handle isInit
            if (type != null) {
                final var fieldNum = getOrAllocateFieldId(type, allocMap);

                output.add(new InsnNode(Opcodes.DUP));
                output.add(Util.loadLocal(remapLVT(localIndex), type));
                output.add(scratchField(Opcodes.PUTFIELD, fieldNum, type));

                resumeInstr.add(new InsnNode(Opcodes.DUP));
                resumeInstr.add(scratchField(Opcodes.GETFIELD, fieldNum, type));
                resumeInstr.add(Util.storeLocal(remapLVT(localIndex), type));
            } else {
                output.add(new InsnNode(Opcodes.POP));
                resumeInstr.add(new InsnNode(Opcodes.ACONST_NULL));
                resumeInstr.add(new VarInsnNode(Opcodes.ASTORE, remapLVT(localIndex)));
            }
        });

        output.add(new InsnNode(Opcodes.POP));
        resumeInstr.add(new InsnNode(Opcodes.POP));

        return () -> output.add(resumeInstr);
    }

    private Runnable codegenSuspendStack(AnalyzerAdapter frame, Object2IntMap<Type> allocMap) {
        final var resumeReversed = new ArrayList<AbstractInsnNode>();
        final var output = taskImplRun.instructions;

        if (frame.stack.size() > 1) {
            output.add(new VarInsnNode(Opcodes.ASTORE, LVT_SCRATCH_SMALL));
            Util.forEachStack(frame, 1, (type, isInit) -> {
                // TODO: handle isInit
                if (type == null) {
                    output.add(new InsnNode(Opcodes.POP));
                    resumeReversed.add(new InsnNode(Opcodes.ACONST_NULL));
                } else {
                    final var fieldId = getOrAllocateFieldId(type, allocMap);
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

            output.add(new VarInsnNode(Opcodes.ALOAD, LVT_SCRATCH_SMALL));
        }

        return () -> {
            for (int i = resumeReversed.size() - 1; i >= 0; i--) {
                output.add(resumeReversed.get(i));
            }
        };
    }

    private void codegenSuspend(InsnList output, AnalyzerAdapter frame) {
        final var allocMap = new Object2IntArrayMap<Type>();
        final var resumeLabel = new LabelNode();

        final var resumeLocal = codegenSuspendLocals(frame, allocMap);
        final var resumeStack = codegenSuspendStack(frame, allocMap);

        output.add(new VarInsnNode(Opcodes.ALOAD, LVT_THIS));
        output.add(new LdcInsnNode(resumeLabels.size()));
        output.add(BASIC_TASK_CLASS_SUSPEND.instr(Opcodes.INVOKESTATIC));
        output.add(new InsnNode(Opcodes.RETURN));

        output.add(resumeLabel);
        resumeLocal.run();
        resumeStack.run();
        final var checkEhExit = new LabelNode();
        output.add(new VarInsnNode(Opcodes.ALOAD, LVT_RES_VAL));

        // handle exception case
        output.add(new VarInsnNode(Opcodes.ILOAD, LVT_IS_EXCEPTION));
        output.add(new JumpInsnNode(Opcodes.IFEQ, checkEhExit));
        output.add(new TypeInsnNode(Opcodes.CHECKCAST, THROWABLE_CLASS_BIN));
        output.add(new InsnNode(Opcodes.ATHROW));
        output.add(checkEhExit);

        resumeLabels.add(resumeLabel);
    }

    private void codegenReturn(InsnList output, MethodInsnNode methodInstr) {
        if (Type.getArgumentCount(methodInstr.desc) == 0) {
            output.add(new VarInsnNode(Opcodes.ALOAD, LVT_THIS));
            output.add(new InsnNode(Opcodes.ACONST_NULL));
        } else {
            output.add(new VarInsnNode(Opcodes.ALOAD, LVT_THIS));
            output.add(new InsnNode(Opcodes.SWAP));
        }

        output.add(BASIC_TASK_CLASS_COMPLETE_SUCCESS.instr(Opcodes.INVOKEVIRTUAL));
        output.add(new InsnNode(Opcodes.RETURN));
    }

    private void codegenRunImplCopyBody(Map<LabelNode, LabelNode> labelCloner) {
        final var instructions = coMethod.instructions.toArray();
        final var output = taskImplRun.instructions;

        var analyzer = new AnalyzerAdapter(coMethodOwner.name, coMethod.access, coMethod.name, coMethod.desc, null);

        // process main instruction
        for (int i = 0; i < instructions.length; i++) {
            final var instruction = instructions[i];

            if (instruction instanceof VarInsnNode varInstNode) {
                output.add(new VarInsnNode(varInstNode.getOpcode(), remapLVT(varInstNode.var)));
            } else if (instruction instanceof IincInsnNode iincInsnNode) {
                output.add(new IincInsnNode(remapLVT(iincInsnNode.var), iincInsnNode.incr));
            } else if (isCoMethod(instruction) && analyzer.stack != null) {
                final var methodInstr = (MethodInsnNode) instruction;
                switch (methodInstr.name) {
                case AWAIT_KW -> codegenSuspend(output, analyzer);
                case RET_KW -> {
                    if (i + 1 >= instructions.length || instructions[i + 1].getOpcode() != Opcodes.ARETURN) {
                        throw new AssertionError();
                    }
                    codegenReturn(output, methodInstr);
                    i++;
                }
                case CURRENT_EXECUTOR_KW -> {
                    output.add(new VarInsnNode(Opcodes.ALOAD, LVT_THIS));
                    output.add(BASIC_TASK_CLASS_GET_EXECUTOR.instr(Opcodes.INVOKEVIRTUAL));
                }
                default -> throw new IllegalStateException();
                }
            } else {
                // copy over the instruction
                output.add(instruction.clone(labelCloner));
            }

            instruction.accept(analyzer);
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

        output.add(new VarInsnNode(Opcodes.ALOAD, LVT_THIS));
        output.add(new InsnNode(Opcodes.SWAP));
        output.add(BASIC_TASK_CLASS_COMPLETE_ERROR.instr(Opcodes.INVOKEVIRTUAL));
        output.add(new InsnNode(Opcodes.RETURN));

        taskImplRun.tryCatchBlocks.add(new TryCatchBlockNode(beginLabel, endLabel, catcher, THROWABLE_CLASS_BIN));
    }

    private void codegenRunImplCopyLVT(LabelNode beginLabel, LabelNode endLabel, Map<LabelNode, LabelNode> mapper) {
        if (coMethod.localVariables == null) {
            taskImplRun.localVariables = new ArrayList<>();
        } else {
            taskImplRun.localVariables = coMethod.localVariables.stream()
                .map(old -> new LocalVariableNode(
                    old.name,
                    old.desc,
                    old.signature,
                    mapper.get(old.start),
                    mapper.get(old.end),
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

        // generate preamble
        final var entryLabel = new LabelNode();
        resumeLabels.add(entryLabel);
        output.add(entryLabel);

        output.add(new VarInsnNode(Opcodes.ALOAD, LVT_THIS));

        Util.forEachArgType(argTypes, (index, argType) -> {
            output.add(new InsnNode(Opcodes.DUP));
            output.add(argField(Opcodes.GETFIELD, index, argType));
            output.add(Util.storeLocal(remapLVT(index), argType));
        });

        output.add(new InsnNode(Opcodes.POP));

        // copy body
        codegenRunImplCopyBody(labelCloner);

        // generate the switch table
        output.insert(new TableSwitchInsnNode(
            0,
            resumeLabels.size() - 1,
            entryLabel,
            resumeLabels.toArray(LabelNode[]::new)
        ));
        output.insert(new VarInsnNode(Opcodes.ILOAD, LVT_STATE));

        Util.withMethodBody(output, (start, end) -> codegenRunImplCopyLVT(start, end, labelCloner));
        Util.withMethodBody(output, this::codegenRunImplEhWrapper);
    }

    private void codegenConstructor() {
        final var output = taskImplClassCons.instructions;

        output.add(new VarInsnNode(Opcodes.ALOAD, LVT_THIS));
        output.add(new InsnNode(Opcodes.DUP));
        output.add(BASIC_TASK_CLASS_CONSTRUCTOR.instr(Opcodes.INVOKESPECIAL));

        Util.forEachArgType(argTypes, (index, argType) -> {
            output.add(new InsnNode(Opcodes.DUP));
            // need to add 1 because 0 is used as "this"
            output.add(Util.loadLocal(1 + index, argType));
            output.add(argField(Opcodes.PUTFIELD, index, argType));
        });

        output.add(new InsnNode(Opcodes.POP));
        output.add(new InsnNode(Opcodes.RETURN));
    }

    private void codegenCoMethod() {
        final var output = new InsnList();
        coMethod.instructions = output;

        output.add(new TypeInsnNode(Opcodes.NEW, taskImplClassName));
        output.add(new InsnNode(Opcodes.DUP));

        Util.forEachArgType(argTypes, (index, argType) -> coMethod.instructions.add(Util.loadLocal(index, argType)));

        output.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, taskImplClassName, "<init>", consDesc));
        output.add(new InsnNode(Opcodes.ARETURN));
        coMethod.localVariables.clear();
    }

    private void codegenFields() {
        final var access = Opcodes.ACC_PRIVATE;
        final var fields = taskImplClass.fields;

        Util.forEachArgType(argTypes, (index, argType) -> {
            fields.add(new FieldNode(access, "arg" + index, argType.getDescriptor(), null, null));
        });

        localStoragePool.forEach((type, integers) -> {
            integers.forEach(i -> {
                fields.add(new FieldNode(access, "l" + i, type.getDescriptor(), null, null));
            });
        });
    }

    private ClassNode codegen() {
        codegenRunImpl();
        codegenConstructor();
        codegenCoMethod();
        codegenFields();
        return taskImplClass;
    }

    public static ClassNode generate(ClassNode owner, MethodNode node, int id) {
        final var output = new MethodTransformer(owner, node, id).codegen();

        if (Boolean.getBoolean("coro.debug")) {
            TraceClassVisitor tcv = new TraceClassVisitor(new PrintWriter(System.out));
            output.accept(tcv);
        }

        return output;
    }
}
