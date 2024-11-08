package com.floweytf.coro.ap.impl;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Name;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

public class ValidateCoro extends TreeScanner {
    private static class MethodContext {
        private final JCTree.JCMethodDecl method;
        private final CoroutineKind kind;
        private int syncBlockNest;

        private MethodContext(JCTree.JCMethodDecl method, CoroutineKind kind) {
            this.method = method;
            this.kind = kind;
        }
    }

    private static final MethodContext NIL = new MethodContext(null, null);

    private final Deque<MethodContext> methodDeclContext = new ArrayDeque<>();
    private JCTree.JCReturn lastReturn = null;

    private final CoroutineProcessorContext context;

    public ValidateCoro(CoroutineProcessorContext context) {
        this.context = context;
    }

    private MethodContext currentContext() {
        final var entry = Objects.requireNonNull(methodDeclContext.peek());

        if (entry == NIL) {
            throw new NullPointerException("top entry is nil");
        }

        return entry;
    }

    private boolean typeMatch(Symbol symbol, Name name) {
        if (symbol instanceof Symbol.ClassSymbol classSymbol) {
            return classSymbol.fullname == name;
        }

        return false;
    }

    private boolean typeMatch(Type type, Name name) {
        return typeMatch(type.tsym, name);
    }

    private CoroutineKind getKindFromReturnType(JCTree tree) {
        if (!(tree instanceof JCTree.JCTypeApply apply)) {
            return CoroutineKind.NONE;
        }

        if (!(apply.getType() instanceof JCTree.JCIdent ident)) {
            return CoroutineKind.NONE;
        }

        if (typeMatch(ident.type, context.getTaskClassName())) {
            return CoroutineKind.TASK;
        }

        return CoroutineKind.NONE;
    }

    @Override
    public void visitSynchronized(JCTree.JCSynchronized tree) {
        currentContext().syncBlockNest++;
        super.visitSynchronized(tree);
        currentContext().syncBlockNest--;
    }

    @Override
    public void visitClassDef(JCTree.JCClassDecl tree) {
        methodDeclContext.push(NIL);
        super.visitClassDef(tree);
        methodDeclContext.pop();
    }

    @Override
    public void visitLambda(JCTree.JCLambda tree) {
        methodDeclContext.push(NIL);
        super.visitLambda(tree);
        methodDeclContext.pop();
    }

    @Override
    public void visitMethodDef(JCTree.JCMethodDecl tree) {
        final var coroAnnotation = tree.mods.annotations
            .stream()
            .filter(ann -> typeMatch(ann.type, context.getCoroutineAnnotationName()))
            .findFirst();

        var kind = CoroutineKind.NONE;

        if (coroAnnotation.isPresent()) {
            if ((tree.mods.flags & Flags.SYNCHRONIZED) != 0) {
                context.reportError(coroAnnotation.get(), "Coroutine methods cannot be synchronized");
            }

            kind = getKindFromReturnType(tree.restype);

            if (kind == CoroutineKind.NONE) {
                context.reportError(tree.restype, "Coroutine methods must return either Generator<T> or Task<T>");
            }
        }

        if (kind != CoroutineKind.NONE) {
            context.reportCoroutineMethod(tree);
        }

        methodDeclContext.push(new MethodContext(tree, kind));
        super.visitMethodDef(tree);
        methodDeclContext.pop();
    }

    @Override
    public void visitApply(JCTree.JCMethodInvocation tree) {
        Symbol symbol = null;
        if (tree.meth instanceof JCTree.JCIdent ident) {
            symbol = ident.sym;
        } else if (tree.meth instanceof JCTree.JCFieldAccess fieldAccess) {
            symbol = fieldAccess.sym;
        }

        if (symbol == null) {
            return;
        }

        if (!typeMatch(symbol.owner, context.getCoClassName())) {
            return;
        }

        final var methodContext = currentContext();

        if (methodContext.kind == CoroutineKind.NONE) {
            context.reportError(tree.meth, "Co.* keyword methods cannot be used outside of a Coroutine method");
        }

        if (symbol.name == context.getAwaitName()) {
            if (methodContext.syncBlockNest > 0) {
                context.reportError(tree.meth, "Co.await() cannot be used in a synchronized block");
            }
        } else if (symbol.name == context.getYieldName()) {
            if (methodContext.kind != CoroutineKind.GENERATOR) {
                context.reportError(tree.meth, "Co.yield() must be used in a generator");
            }
        } else if (symbol.name == context.getRetName()) {
            if (lastReturn == null || lastReturn.expr != tree) {
                context.reportError(tree.meth, "Co.ret() cannot be used as a free method; it must be used as `return " +
                    "Co.ret`");
            }
        }
    }

    @Override
    public void visitReturn(JCTree.JCReturn tree) {
        lastReturn = tree;
        super.visitReturn(tree);
        lastReturn = null;
    }
}
