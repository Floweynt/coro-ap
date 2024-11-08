package com.floweytf.coro.ap.impl;

import com.floweytf.coro.ap.PluginDelegate;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.tree.JCTree;

public class PluginDelegateImpl implements PluginDelegate, TaskListener {
    private CoroutineProcessorContext coroutineProcessorContext;

    private void onFinishAnalysis(TaskEvent e) {
        final var tree = (JCTree.JCCompilationUnit) e.getCompilationUnit();
        coroutineProcessorContext.setSource(e.getSourceFile());
        tree.accept(new ValidateCoro(coroutineProcessorContext));
    }

    @Override
    public void init(JavacTask javacTask, String... strings) {
        final var context = ((BasicJavacTask) javacTask).getContext();
        coroutineProcessorContext = new CoroutineProcessorContext(context);

        javacTask.addTaskListener(this);
    }

    @Override
    public void finished(TaskEvent e) {
        if (e.getKind() == TaskEvent.Kind.ANALYZE) {
            onFinishAnalysis(e);
        }
    }
}
