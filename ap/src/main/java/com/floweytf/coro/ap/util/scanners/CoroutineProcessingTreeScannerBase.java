package com.floweytf.coro.ap.util.scanners;

import com.floweytf.coro.ap.CoroutineNames;
import com.floweytf.coro.ap.CoroutineKind;
import com.floweytf.coro.ap.DirectiveKind;
import com.floweytf.coro.ap.util.Diagnostics;
import com.floweytf.coro.ap.util.Frame;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.TargetType;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCTypeApply;
import com.sun.tools.javac.util.Name;
import org.jetbrains.annotations.Nullable;

import static com.sun.tools.javac.code.Symbol.MethodSymbol;
import static com.sun.tools.javac.tree.JCTree.JCLambda;

public abstract class CoroutineProcessingTreeScannerBase extends ContextTrackingTreeScanner {
    protected record InvocationData(@Nullable DirectiveKind directive, @Nullable MethodSymbol methodSymbol) {
    }

    protected final Diagnostics diagnostics;
    protected final CoroutineNames names;
    protected final Frame<CoroutineKind> coroutineKind = new Frame<>();

    public CoroutineProcessingTreeScannerBase(final CoroutineNames names, final Diagnostics diagnostics) {
        this.diagnostics = diagnostics;
        this.names = names;
    }

    protected static Symbol getSymbol(final JCExpression expression) {
        Symbol symbol = null;
        if (expression instanceof final JCIdent identifier) {
            symbol = identifier.sym;
        } else if (expression instanceof final JCFieldAccess fieldAccess) {
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
        if (!(tree instanceof final JCTypeApply apply)) {
            return CoroutineKind.NONE;
        }

        if (!(apply.getType() instanceof final JCIdent ident)) {
            return CoroutineKind.NONE;
        }

        if (typeMatch(ident.type, names.taskClassName())) {
            return CoroutineKind.TASK;
        }

        return CoroutineKind.NONE;
    }

    protected final InvocationData readInvocationData(final JCMethodInvocation tree) {
        final var symbol = getSymbol(tree.meth);
        final var methodSymbol = symbol instanceof final MethodSymbol ms ? ms : null;

        final DirectiveKind kind;

        if (symbol == null || !typeMatch(symbol.owner, names.coClassName())) {
            kind = null;
        } else if (symbol.name == names.awaitName()) {
            kind = DirectiveKind.AWAIT;
        } else if (symbol.name == names.retName()) {
            kind = DirectiveKind.RETURN;
        } else if (symbol.name == names.currentExecutorName()) {
            kind = DirectiveKind.CURRENT_EXECUTOR;
        } else {
            kind = null;
        }

        return new InvocationData(kind, methodSymbol);
    }

    @Override
    public void visitClassDef(final JCClassDecl tree) {
        coroutineKind.push(null, () -> super.visitClassDef(tree));
    }

    @Override
    public void visitMethodDef(final JCMethodDecl tree) {
        final var coroAnnotation = tree.mods.annotations
            .stream()
            .filter(ann -> typeMatch(ann.type, names.coroutineAnnotationName()))
            .findFirst();

        var kind = CoroutineKind.NONE;

        if (coroAnnotation.isPresent()) {
            if ((tree.mods.flags & Flags.SYNCHRONIZED) != 0) {
                diagnostics.reportError(coroAnnotation.get(), "Coroutine methods cannot be synchronized");
            }

            kind = getKindFromReturnType(tree.restype);

            if (kind == CoroutineKind.NONE) {
                diagnostics.reportError(tree.restype, "Coroutine methods must return either Generator<T> or Task<T>");
            }
        }

        if (kind != CoroutineKind.NONE) {
            onCoroutineMethod(currentClass.get());
        }

        coroutineKind.push(kind, () -> super.visitMethodDef(tree));
    }

    private boolean[] buildIsCoroArgTable(final InvocationData data, final JCMethodInvocation tree) {
        // TODO: cache this on methodSymbol
        final var mSym = data.methodSymbol();
        final var flags = new boolean[tree.args.size()];

        if (mSym != null && mSym.getMetadata() != null) {
            for (final var attr : mSym.getMetadata().getTypeAttributes()) {
                if (attr.position.type == TargetType.METHOD_FORMAL_PARAMETER &&
                    attr.type.tsym.flatName() == names.makeCoroAnnotationName()) {
                    flags[attr.position.parameter_index] = true;
                }
            }
        }

        return flags;
    }

    @Override
    public void visitLambda(final JCLambda tree) {
        coroutineKind.push(CoroutineKind.NONE, () -> super.visitLambda(tree));
    }

    @Override
    public void visitApply(final JCMethodInvocation tree) {
        final var invokeData = readInvocationData(tree);
        final var directive = invokeData.directive();

        scan(tree.typeargs);
        scan(tree.meth);

        final var isCoroArg = buildIsCoroArgTable(invokeData, tree);
        int argIdx = 0;
        for (final var arg : tree.args) {
            if (isCoroArg[argIdx] && arg instanceof final JCLambda lambda) {
                // make this lambda a coroutine!
                onCoroutineLambda(currentClass.get(), lambda);
                coroutineKind.push(CoroutineKind.TASK, () -> super.visitLambda(lambda));
            } else {
                arg.accept(this);
            }
            argIdx++;
        }

        if (directive != null) {
            visitCoroutineMethod(directive, tree);
        }
    }

    protected abstract void onCoroutineMethod(JCClassDecl declaringClass);

    protected abstract void onCoroutineLambda(JCClassDecl declaringClass, JCLambda lambda);

    protected abstract void visitCoroutineMethod(final DirectiveKind directive, final JCMethodInvocation invocation);
}
