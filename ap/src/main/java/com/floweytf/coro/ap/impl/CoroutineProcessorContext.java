package com.floweytf.coro.ap.impl;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.DiagnosticSource;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.JavacMessages;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.ResourceBundle;
import javax.tools.JavaFileObject;
import org.jetbrains.annotations.NotNull;

public class CoroutineProcessorContext {
    private static final String DIAGNOSTIC_KEY = "com.floweytf.coro.ap";

    private final Log log;
    private final JCDiagnostic.Factory diagFactory;
    private final Name coroutineAnnotationName;
    private final Name coClassName;
    private final Name taskClassName;
    private final Name awaitName;
    private final Name yieldName;
    private final Name retName;

    private DiagnosticSource source;

    private final List<JCTree.JCMethodDecl> coroutineMethods = new ArrayList<>();

    public CoroutineProcessorContext(Context context) {
        log = Log.instance(context);
        diagFactory = JCDiagnostic.Factory.instance(context);

        final var names = Names.instance(context);
        coroutineAnnotationName = names.fromString("com.floweytf.coro.annotations.Coroutine");
        coClassName = names.fromString("com.floweytf.coro.Co");
        taskClassName = names.fromString("com.floweytf.coro.concepts.Task");
        awaitName = names.fromString("await");
        yieldName = names.fromString("yield");
        retName = names.fromString("ret");

        JavacMessages.instance(context).add(locale -> new ResourceBundle() {
            @Override
            protected Object handleGetObject(@NotNull String key) {
                return "{0}";
            }

            @Override
            public @NotNull Enumeration<String> getKeys() {
                return Collections.enumeration(List.of(DIAGNOSTIC_KEY));
            }
        });
    }

    public Name getCoroutineAnnotationName() {
        return coroutineAnnotationName;
    }

    public Name getAwaitName() {
        return awaitName;
    }

    public Name getCoClassName() {
        return coClassName;
    }

    public Name getRetName() {
        return retName;
    }

    public Name getTaskClassName() {
        return taskClassName;
    }

    public Name getYieldName() {
        return yieldName;
    }

    public void setSource(JavaFileObject source) {
        this.source = new DiagnosticSource(source, log);
    }

    public void reportError(
        JCDiagnostic.DiagnosticPosition pos,
        String message
    ) {
        log.report(diagFactory.error(JCDiagnostic.DiagnosticFlag.SYNTAX, source, pos, DIAGNOSTIC_KEY, message));
    }

    public void reportCoroutineMethod(JCTree.JCMethodDecl decl) {
        coroutineMethods.add(decl);
    }
}
