package com.floweytf.coro.ap.entry;

import com.floweytf.coro.ap.util.ReflectTool;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import java.util.List;

public class PluginImpl implements Plugin {
    @Override
    public String getName() {
        return "coroutine-processor";
    }

    public boolean autoStart() {
        return true; // force the plugin to start automagically
    }

    @Override
    public void init(JavacTask javacTask, String... strings) {
        addOpens();
        PluginDelegate.createInstance().init(javacTask, strings);
    }

    private static final List<String> MODULES = List.of(
        "code", "comp", "file", "main", "model", "parser",
        "processing", "tree", "util", "jvm", "api"
    );

    // stolen from Lombok - allow us to access JVM internals
    private static void addOpens() {
        final var jdkCompilerModule = ModuleLayer.boot().findModule("jdk.compiler").orElseThrow();
        final var ownModule = PluginImpl.class.getModule();

        try {
            final var m = ReflectTool.getMethod(Module.class, "implAddOpens", String.class, Module.class);
            for (final var p : MODULES) {
                m.invoke(jdkCompilerModule, "com.sun.tools.javac." + p, ownModule);
            }
        } catch (Exception ignore) {
        }
    }
}
