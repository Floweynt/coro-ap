package com.floweytf.coro.ap;

import com.sun.source.util.JavacTask;

public interface PluginDelegate {
    void init(JavacTask javacTask, String... strings);

    static PluginDelegate createInstance() {
        try {
            return (PluginDelegate) Class.forName("com.floweytf.coro.ap.impl.PluginDelegateImpl")
                .getConstructor()
                .newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
