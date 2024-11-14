package com.floweytf.coro.ap.pass;

import com.floweytf.coro.ap.Constants;
import com.floweytf.coro.ap.CoroNames;
import com.floweytf.coro.ap.CoroutineKind;
import com.floweytf.coro.ap.Coroutines;
import com.sun.source.util.TaskEvent;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.DiagnosticSource;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Name;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

public class ValidatePass extends TreeScanner {
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

    private final Coroutines coroutines;
    private final Log log;
    private final CoroNames names;
    private final JCDiagnostic.Factory diagFactory;
    private final DiagnosticSource source;

    public ValidatePass(Coroutines coroutines, TaskEvent event) {
        this.coroutines = coroutines;
        log = Log.instance(coroutines.getContext());
        source = new DiagnosticSource(event.getSourceFile(), log);
        diagFactory = JCDiagnostic.Factory.instance(coroutines.getContext());
        names = coroutines.coroNames();
    }

    private void reportError(JCDiagnostic.DiagnosticPosition pos, String message) {
        log.report(diagFactory.error(
            JCDiagnostic.DiagnosticFlag.SYNTAX, source, pos, Constants.DIAGNOSTIC_KEY, message
        ));
    }

    private MethodContext currentContext() {
        final var entry = Objects.requireNonNull(methodDeclContext.peek());

        if (entry == NIL) {
            throw new NullPointerException("top entry is nil");
        }

        return entry;
    }

    private boolean typeMatch(Symbol symbol, Name name) {
        return symbol.flatName() == name;
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

        if (typeMatch(ident.type, names.taskClassName())) {
            return CoroutineKind.TASK;
        }

        if (typeMatch(ident.type, names.generatorClassName())) {
            return CoroutineKind.GENERATOR;
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
            .filter(ann -> typeMatch(ann.type, names.coroutineAnnotationName()))
            .findFirst();

        var kind = CoroutineKind.NONE;

        if (coroAnnotation.isPresent()) {
            if ((tree.mods.flags & Flags.SYNCHRONIZED) != 0) {
                reportError(coroAnnotation.get(), "Coroutine methods cannot be synchronized");
            }

            kind = getKindFromReturnType(tree.restype);

            if (kind == CoroutineKind.NONE) {
                reportError(tree.restype, "Coroutine methods must return either Generator<T> or Task<T>");
            }
        }

        if (kind != CoroutineKind.NONE) {
            coroutines.reportCoroutineMethod(tree, kind);
        }

        methodDeclContext.push(new MethodContext(tree, kind));
        super.visitMethodDef(tree);
        methodDeclContext.pop();
    }

    private Symbol getMethodSymbol(JCTree.JCExpression expression) {
        Symbol symbol = null;
        if (expression instanceof JCTree.JCIdent ident) {
            symbol = ident.sym;
        } else if (expression instanceof JCTree.JCFieldAccess fieldAccess) {
            symbol = fieldAccess.sym;
        }
        return symbol;
    }

    @Override
    public void visitApply(JCTree.JCMethodInvocation tree) {
        Symbol symbol = getMethodSymbol(tree.meth);

        if (symbol == null) {
            return;
        }

        if (!typeMatch(symbol.owner, names.coClassName())) {
            return;
        }

        final var methodContext = currentContext();

        if (methodContext.kind == CoroutineKind.NONE) {
            reportError(tree.meth, "Co.* keyword methods cannot be used outside of a Coroutine method");
            return;
        }

        if (symbol.name == names.retName()) {
            if (lastReturn == null || lastReturn.expr != tree) {
                reportError(tree.meth, "Co.ret() cannot be used as a free method; it must be used as `return " +
                    "Co.ret`");
            }

            return;
        }

        if (methodContext.kind == CoroutineKind.GENERATOR) {
            if (symbol.name == names.yieldName()) {
                if (methodContext.syncBlockNest > 0) {
                    reportError(tree.meth, "Co.yield() cannot be used in a synchronized block");
                }
            } else {
                reportError(tree.meth, "This Co#<method> cannot be used in a generator");
            }
        } else if (methodContext.kind == CoroutineKind.TASK) {
            if (symbol.name == names.awaitName()) {
                if (methodContext.syncBlockNest > 0) {
                    reportError(tree.meth, "Co.await() cannot be used in a synchronized block");
                }
            } else if (symbol.name == names.currentExecutorName()) {
            } else {
                reportError(tree.meth, "This Co#<method> cannot be used in a task");
            }
        }
    }

    private void validateReturn(JCTree.JCReturn tree) {
        if (currentContext().kind == CoroutineKind.NONE) {
            return;
        }
        if (!(tree.expr instanceof JCTree.JCMethodInvocation invocation)) {
            reportError(tree, "return is not allowed in Coroutine method; it must be used as `return Co.ret`");
            return;
        }

        Symbol symbol = getMethodSymbol(invocation.meth);

        if (symbol == null || !typeMatch(symbol.owner, names.coClassName()) || symbol.name != names.retName()) {
            reportError(tree, "return is not allowed in Coroutine method; it must be used as `return Co.ret`");
        }
    }

    @Override
    public void visitReturn(JCTree.JCReturn tree) {
        lastReturn = tree;
        validateReturn(tree);
        super.visitReturn(tree);
        lastReturn = null;
    }
}
