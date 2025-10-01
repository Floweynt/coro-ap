package com.floweytf.coro.ap;

import com.floweytf.coro.ap.pass.TransformPass;
import com.floweytf.coro.ap.pass.ValidatePass;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCLambda;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JavacMessages;
import com.sun.tools.javac.util.Names;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.ResourceBundle;
import org.jetbrains.annotations.NotNull;

import static com.sun.tools.javac.code.Symbol.ClassSymbol;

public class Coroutines implements TaskListener {
    private final Context context;
    private final CoroutineNames coroutineNames;
    private final Debug debug = new Debug();
    private long compileTime;
    private final AnalysisReport report;

    public Coroutines(final Context context) {
        this.context = context;
        coroutineNames = new CoroutineNames(Names.instance(context));

        JavacMessages.instance(context).add(locale -> new ResourceBundle() {
            @Override
            protected Object handleGetObject(@NotNull final String key) {
                return "{0}";
            }

            @Override
            public @NotNull Enumeration<String> getKeys() {
                return Collections.enumeration(List.of(Constants.DIAGNOSTIC_KEY));
            }
        });

        this.report = new AnalysisReport(this);
    }

    @Override
    public void finished(final TaskEvent event) {
        switch (event.getKind()) {
        case ANALYZE -> {
            try {
                final var startTime = System.currentTimeMillis();
                ((JCCompilationUnit) event.getCompilationUnit()).accept(new ValidatePass(this, event));
                final var finalTime = System.currentTimeMillis();
                compileTime += finalTime - startTime;
            } catch (final Throwable e) {
                final var sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                debug.warnPrintln(
                    "while parsing file %s: %s\n%s",
                    event.getCompilationUnit().getSourceFile().getName(),
                    e.getMessage(),
                    sw
                );

                // rethrow
                throw e;
            }
        }
        case GENERATE -> report.reportGeneratedSymbol((ClassSymbol) event.getTypeElement());
        case COMPILATION -> {
            debug.perfPrintln("analysis time: %sms", compileTime);
            compileTime = 0;

            final var startTime = System.currentTimeMillis();
            try {
                new TransformPass(this).process(report);
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
            final var finalTime = System.currentTimeMillis();

            debug.perfPrintln("codegen time: %sms", finalTime - startTime);
        }
        }
    }

    public Context getContext() {
        return context;
    }

    public CoroutineNames coroutineNames() {
        return coroutineNames;
    }

    public Debug debug() {
        return debug;
    }

    public void reportCoroutineMethod(final ClassSymbol enclosingClass) {
        report.reportCoroutineMethod(enclosingClass);
    }

    public void reportCoroutineLambdaMethod(final ClassSymbol enclosingClass, final JCLambda lambda) {
        report.reportLambda(enclosingClass, lambda);
    }
}
