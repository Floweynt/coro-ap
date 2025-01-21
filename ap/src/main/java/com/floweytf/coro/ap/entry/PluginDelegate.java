package com.floweytf.coro.ap.entry;

import com.sun.source.util.JavacTask;

/**
 * A wrapper interface for the actual plugin implementation. This is needed, since we can't afford to accidentally
 * classload stuff before we open up the packages.
 */
public interface PluginDelegate {
    /**
     * Creates an instance of the implementation.
     *
     * @return An instance of the implementation.
     */
    static PluginDelegate createInstance() {
        try {
            return (PluginDelegate) Class.forName("com.floweytf.coro.ap.PluginDelegateImpl")
                .getConstructor()
                .newInstance();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Initialize the plugin.
     *
     * @param javacTask The task.
     * @param strings   Compiler arguments.
     */
    void init(JavacTask javacTask, String... strings);
}
