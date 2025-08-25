package com.floweytf.coro.ap.util.scanners;

import com.floweytf.coro.ap.CoroNames;
import com.floweytf.coro.ap.CoroutineKind;
import com.floweytf.coro.ap.DirectiveKind;
import com.floweytf.coro.ap.util.ErrorReporter;
import com.floweytf.coro.ap.util.Frame;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Name;
import org.jetbrains.annotations.Nullable;

public abstract class CoroutineProcessingTreeScannerBase extends ContextTrackingTreeScanner {
    protected final ErrorReporter errorReporter;
    protected final CoroNames names;
    protected final Frame<CoroutineKind> coroutineKind = new Frame<>();

    public CoroutineProcessingTreeScannerBase(final CoroNames names, final ErrorReporter errorReporter) {
        this.errorReporter = errorReporter;
        this.names = names;
    }

    private Symbol getMethodSymbol(final JCTree.JCExpression expression) {
        Symbol symbol = null;
        if (expression instanceof final JCTree.JCIdent identifier) {
            symbol = identifier.sym;
        } else if (expression instanceof final JCTree.JCFieldAccess fieldAccess) {
            symbol = fieldAccess.sym;
        }
        return symbol;
    }

    private boolean typeMatch(final Symbol symbol, final Name name) {
        return symbol.flatName() == name;
    }

    private boolean typeMatch(final Type type, final Name name) {
        return typeMatch(type.tsym, name);
    }

    private CoroutineKind getKindFromReturnType(final JCTree tree) {
        if (!(tree instanceof final JCTree.JCTypeApply apply)) {
            return CoroutineKind.NONE;
        }

        if (!(apply.getType() instanceof final JCTree.JCIdent ident)) {
            return CoroutineKind.NONE;
        }

        if (typeMatch(ident.type, names.taskClassName())) {
            return CoroutineKind.TASK;
        }

        return CoroutineKind.NONE;
    }

    protected final @Nullable DirectiveKind getDirective(final JCTree.JCMethodInvocation tree) {
        final Symbol symbol = getMethodSymbol(tree.meth);

        if (symbol == null || !typeMatch(symbol.owner, names.coClassName())) {
            return null;
        }

        if (symbol.name == names.awaitName()) {
            return DirectiveKind.AWAIT;
        } else if (symbol.name == names.retName()) {
            return DirectiveKind.RETURN;
        } else if (symbol.name == names.coroutineName()) {
            return DirectiveKind.COROUTINE;
        } else if (symbol.name == names.currentExecutorName()) {
            return DirectiveKind.CURRENT_EXECUTOR;
        } else {
            throw new IllegalStateException(
                "JCMethodInvocation on unknown Co method"
            );
        }
    }

    @Override
    public void visitClassDef(final JCTree.JCClassDecl tree) {
        coroutineKind.push(null, () -> super.visitClassDef(tree));
    }

    @Override
    public void visitMethodDef(final JCTree.JCMethodDecl tree) {
        final var coroAnnotation = tree.mods.annotations
            .stream()
            .filter(ann -> typeMatch(ann.type, names.coroutineAnnotationName()))
            .findFirst();

        var kind = CoroutineKind.NONE;

        if (coroAnnotation.isPresent()) {
            if ((tree.mods.flags & Flags.SYNCHRONIZED) != 0) {
                errorReporter.reportError(coroAnnotation.get(), "Coroutine methods cannot be synchronized");
            }

            kind = getKindFromReturnType(tree.restype);

            if (kind == CoroutineKind.NONE) {
                errorReporter.reportError(tree.restype, "Coroutine methods must return either Generator<T> or Task<T>");
            }
        }

        if (kind != CoroutineKind.NONE) {
            onCoroutineMethod(currentClass.get());
        }

        coroutineKind.push(kind, () -> super.visitMethodDef(tree));
    }

    @Override
    public void visitApply(final JCTree.JCMethodInvocation tree) {
        final var directive = getDirective(tree);

        if (directive == null) {
            super.visitApply(tree);
            return;
        }

        visitCoroutineMethod(directive, tree);
    }

    protected abstract void onCoroutineMethod(JCTree.JCClassDecl declaringClass);

    protected abstract void onCoroutineLambda(JCTree.JCClassDecl declaringClass, int id);

    protected void visitCoroutineMethod(final DirectiveKind directive, final JCTree.JCMethodInvocation invocation) {
        if (directive == DirectiveKind.COROUTINE) {
            final var argument = invocation.args.get(0);

            if (argument instanceof final JCTree.JCLambda lambda) {
                onCoroutineLambda(currentClass.get(), lambdaCount.get());
                coroutineKind.push(CoroutineKind.TASK, () -> super.visitLambda(lambda));
                return;
            }

            // allow fall-thought - if an error occurs, then just assume it's used wrongly and check insides
            errorReporter.reportError(argument, "must be a lambda expression");
        }

        for (final var arg : invocation.args) {
            arg.accept(this);
        }
    }
}
