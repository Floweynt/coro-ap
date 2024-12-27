package com.floweytf.coro.ap.codegen;

import static com.floweytf.coro.ap.Constants.BASIC_GENERATOR_CLASS_BIN;
import static com.floweytf.coro.ap.Constants.BASIC_GENERATOR_COMPLETE_ERROR;
import static com.floweytf.coro.ap.Constants.BASIC_GENERATOR_RUN;
import static com.floweytf.coro.ap.Constants.BASIC_GENERATOR_YIELD_GENERATOR;
import static com.floweytf.coro.ap.Constants.BASIC_GENERATOR_YIELD_VALUE;
import static com.floweytf.coro.ap.Constants.OBJECT_CLASS_BIN;
import static com.floweytf.coro.ap.Constants.OBJECT_TYPE;
import static com.floweytf.coro.ap.Constants.THROWABLE_TYPE;
import static com.floweytf.coro.ap.Constants.YIELD_KW;
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
import org.objectweb.asm.tree.VarInsnNode;

public class GeneratorMethodTransformer extends BasicMethodTransformer {
    private static final int LVT_EXCEPTION = LVT_STATE + 1;
    private static final int LVT_SCRATCH_SMALL = LVT_STATE + 2;
    private static final int LVT_OFFSET = LVT_STATE + 3;

    public GeneratorMethodTransformer(ClassNode coMethodOwner, MethodNode coMethod, int id) {
        super(
            coMethodOwner, coMethod, id, LVT_SCRATCH_SMALL, LVT_OFFSET,
            BASIC_GENERATOR_COMPLETE_ERROR, BASIC_GENERATOR_CLASS_BIN, BASIC_GENERATOR_RUN
        );
    }

    @Override
    protected void genReportSuspend(InsnList output, MethodInsnNode originalMethod) {
        if (Type.getArgumentTypes(originalMethod.desc)[0].equals(OBJECT_TYPE)) {
            output.add(BASIC_GENERATOR_YIELD_VALUE.instr(Opcodes.INVOKESTATIC));
        } else {
            output.add(BASIC_GENERATOR_YIELD_GENERATOR.instr(Opcodes.INVOKESTATIC));
        }
    }

    @Override
    protected void genReportReturn(InsnList output, MethodInsnNode methodInstr) {
        if (Type.getArgumentCount(methodInstr.desc) != 0) {
            output.add(new InsnNode(Opcodes.POP));
        }
    }

    @Override
    protected void genCheckException(InsnList output) {
        final var exit = new LabelNode();
        output.add(new VarInsnNode(Opcodes.ALOAD, LVT_EXCEPTION));
        output.add(new JumpInsnNode(Opcodes.IFNULL, exit));
        output.add(new VarInsnNode(Opcodes.ALOAD, LVT_EXCEPTION));
        output.add(new InsnNode(Opcodes.ATHROW));
        output.add(exit);
        output.add(createNode());
    }

    @Override
    protected void genResume(InsnList output) {
    }

    @Override
    protected void genLocals(LabelNode begin, LabelNode end, List<LocalVariableNode> lvt) {
        lvt.add(new LocalVariableNode("ex", THROWABLE_TYPE.getDescriptor(), null, begin, end, LVT_EXCEPTION));
    }

    @Override
    protected void handleCoMethod(InsnList output, MethodInsnNode methodInstr) {
        if (methodInstr.name.equals(YIELD_KW)) {
            genSuspendPoint(methodInstr);
        } else {
            throw new AssertionError("illegal Co.<method> " + methodInstr.name);
        }
    }
}
