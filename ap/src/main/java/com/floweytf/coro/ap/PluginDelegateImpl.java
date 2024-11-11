package com.floweytf.coro.ap;

import com.floweytf.coro.ap.entry.PluginDelegate;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.api.BasicJavacTask;

public class PluginDelegateImpl implements PluginDelegate, TaskListener {
    @Override
    public void init(JavacTask javacTask, String... strings) {
        final var context = ((BasicJavacTask) javacTask).getContext();
        Coroutines coroutines = new Coroutines(context);
        javacTask.addTaskListener(coroutines);
    }
}
