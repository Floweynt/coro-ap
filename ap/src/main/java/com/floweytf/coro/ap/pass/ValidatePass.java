package com.floweytf.coro.ap.pass;

import com.floweytf.coro.ap.Coroutines;
import com.floweytf.coro.ap.DirectiveKind;
import com.floweytf.coro.ap.util.ErrorReporter;
import com.floweytf.coro.ap.util.scanners.CoroutineProcessingTreeScannerBase;
import com.sun.source.util.TaskEvent;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import java.util.function.UnaryOperator;

import static com.floweytf.coro.ap.CoroutineKind.NONE;
import static com.floweytf.coro.ap.DirectiveKind.RETURN;

public class ValidatePass extends CoroutineProcessingTreeScannerBase {
    private final Coroutines coroutines;

    public ValidatePass(final Coroutines coroutines, final TaskEvent event) {
        super(coroutines.coroNames(), new ErrorReporter(coroutines, event));
        this.coroutines = coroutines;
    }

    // this is a bit of a janky workaround that only exists because we have no other way of passing data
    // into the code generator :/
    private void encodeSuspensionPointData(final JCTree.JCExpression expression, final JCTree tree) {
        final UnaryOperator<Symbol> mapper = symbol -> {
            final var mSym = (Symbol.MethodSymbol) symbol;

            return new Symbol.MethodSymbol(
                mSym.flags_field,
                names.names().fromString(
                    mSym.name.toString() + "@" + errorReporter.getSource().getLineNumber(tree.pos)
                ),
                mSym.type, mSym.owner
            );
        };

        if (expression instanceof final JCTree.JCIdent identifier) {
            identifier.sym = mapper.apply(identifier.sym);
        } else if (expression instanceof final JCTree.JCFieldAccess fieldAccess) {
            fieldAccess.sym = mapper.apply(fieldAccess.sym);
        }
    }

    @Override
    public void visitReturn(final JCTree.JCReturn tree) {
        if (coroutineKind.get() == NONE) {
            return;
        }

        if (tree.expr instanceof final JCMethodInvocation inv && readInvocationData(inv).directive() == RETURN) {
            // only visit the args
            for (final var arg : inv.args) {
                arg.accept(this);
            }

            return;
        }

        errorReporter.reportError(tree.expr, "returning from coroutine must be done via Co.ret");
        super.visitReturn(tree);
    }

    @Override
    protected void visitCoroutineMethod(final DirectiveKind directive, final JCMethodInvocation invocation) {
        switch (directive) {
        case AWAIT -> {
            if (coroutineKind.get() == NONE) {
                errorReporter.reportError(invocation, "Co.await() can only be used inside a coroutine");
            }

            if (isSync.get()) {
                errorReporter.reportError(invocation, "Co.await() cannot appear in a synchronized block");
            } else {
                encodeSuspensionPointData(invocation.meth, invocation);
            }
        }
        case CURRENT_EXECUTOR -> {
            if (coroutineKind.get() == NONE) {
                errorReporter.reportError(invocation, "Co.currentExecutor() can only be used inside a coroutine");
            }
        }
        case RETURN ->
            errorReporter.reportError(invocation, "illegal use of Co.ret, must be in the form `return Co.ret(...)`");
        }
    }

    @Override
    protected void onCoroutineMethod(final JCTree.JCClassDecl declaringClass) {
        coroutines.reportCoroutineMethod(declaringClass.sym);
    }

    @Override
    protected void onCoroutineLambda(final JCTree.JCClassDecl declaringClass, final int id) {
        coroutines.reportCoroutineLambdaMethod(declaringClass.sym, id);
    }
}
