package com.floweytf.coro.ap.util;

import com.floweytf.coro.ap.Constants;
import com.floweytf.coro.ap.Coroutines;
import com.sun.source.util.TaskEvent;
import com.sun.tools.javac.util.DiagnosticSource;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.Log;

public class ErrorReporter {
    private final Log log;
    private final DiagnosticSource source;
    private final JCDiagnostic.Factory diagFactory;

    public ErrorReporter(final Coroutines coroutines, final TaskEvent event) {
        log = Log.instance(coroutines.getContext());
        source = new DiagnosticSource(event.getSourceFile(), log);
        diagFactory = JCDiagnostic.Factory.instance(coroutines.getContext());
    }

    public void reportError(final JCDiagnostic.DiagnosticPosition pos, final String message) {
        log.report(diagFactory.error(
            JCDiagnostic.DiagnosticFlag.SYNTAX, source, pos, Constants.DIAGNOSTIC_KEY, message
        ));
    }

    public DiagnosticSource getSource() {
        return source;
    }
}
