package com.floweytf.coro.ap.codegen;

import static com.floweytf.coro.ap.Constants.AWAIT_KW;
import static com.floweytf.coro.ap.Constants.BASIC_TASK_CLASS_BIN;
import static com.floweytf.coro.ap.Constants.BASIC_TASK_COMPLETE_ERROR;
import static com.floweytf.coro.ap.Constants.BASIC_TASK_COMPLETE_SUCCESS;
import static com.floweytf.coro.ap.Constants.BASIC_TASK_GET_EXECUTOR;
import static com.floweytf.coro.ap.Constants.BASIC_TASK_RUN;
import static com.floweytf.coro.ap.Constants.BASIC_TASK_SUSPEND_HELPER;
import static com.floweytf.coro.ap.Constants.CURRENT_EXECUTOR_KW;
import static com.floweytf.coro.ap.Constants.OBJECT_TYPE;
import static com.floweytf.coro.ap.Constants.THROWABLE_TYPE;
import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public class TaskMethodTransformer extends BasicMethodTransformer {
    private static final int LVT_IS_EXCEPTION = LVT_STATE + 1;
    private static final int LVT_RES_VAL = LVT_STATE + 2;
    private static final int LVT_SCRATCH_SMALL = LVT_STATE + 3;
    private static final int LVT_OFFSET = LVT_STATE + 4;

    public TaskMethodTransformer(ClassNode coMethodOwner, MethodNode coMethod, int id) {
        super(
            coMethodOwner, coMethod, id, LVT_SCRATCH_SMALL, LVT_OFFSET, BASIC_TASK_COMPLETE_ERROR,
            BASIC_TASK_CLASS_BIN, BASIC_TASK_RUN
        );
    }

    @Override
    protected void genReportSuspend(InsnList output, MethodInsnNode originalMethod) {
        output.add(BASIC_TASK_SUSPEND_HELPER.instr(Opcodes.INVOKESTATIC));
    }

    @Override
    protected void genReportReturn(InsnList output, MethodInsnNode methodInstr) {
        if (Type.getArgumentCount(methodInstr.desc) == 0) {
            output.add(new InsnNode(Opcodes.ACONST_NULL));
        }

        output.add(new VarInsnNode(Opcodes.ALOAD, LVT_THIS));
        output.add(BASIC_TASK_COMPLETE_SUCCESS.instr(Opcodes.INVOKESTATIC));
    }

    @Override
    protected void genResume(InsnList output) {
        final var exit = new LabelNode();
        output.add(new VarInsnNode(Opcodes.ALOAD, LVT_RES_VAL));
        output.add(new VarInsnNode(Opcodes.ILOAD, LVT_IS_EXCEPTION));
        output.add(new JumpInsnNode(Opcodes.IFEQ, exit));
        output.add(new TypeInsnNode(Opcodes.CHECKCAST, THROWABLE_TYPE.getInternalName()));
        output.add(new InsnNode(Opcodes.ATHROW));
        output.add(exit);
    }

    @Override
    protected void genLocals(LabelNode begin, LabelNode end, List<LocalVariableNode> lvt) {
        lvt.add(new LocalVariableNode("isEx", Type.BOOLEAN_TYPE.getDescriptor(), null, begin, end, LVT_IS_EXCEPTION));
        lvt.add(new LocalVariableNode("res", OBJECT_TYPE.getDescriptor(), null, begin, end, LVT_RES_VAL));
    }

    @Override
    protected void handleCoMethod(InsnList output, MethodInsnNode methodInstr) {
        if (methodInstr.name.equals(AWAIT_KW)) {
            genSuspendPoint(methodInstr);
        } else if (methodInstr.name.equals(CURRENT_EXECUTOR_KW)) {
            output.add(new VarInsnNode(Opcodes.ALOAD, LVT_THIS));
            output.add(BASIC_TASK_GET_EXECUTOR.instr(Opcodes.INVOKEVIRTUAL));
        } else {
            throw new AssertionError("illegal Co.<method> " + methodInstr.name);
        }
    }
}
