package com.floweytf.coro.ap;

import com.floweytf.coro.ap.entry.PluginDelegate;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.api.BasicJavacTask;

public class PluginDelegateImpl implements PluginDelegate {
    @Override
    public void init(final JavacTask javacTask, final String... strings) {
        final var context = ((BasicJavacTask) javacTask).getContext();
        final Coroutines coroutines = new Coroutines(context);
        javacTask.addTaskListener(coroutines);
    }
}
