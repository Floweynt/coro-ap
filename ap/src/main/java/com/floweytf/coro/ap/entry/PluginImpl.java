package com.floweytf.coro.ap.entry;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import java.util.List;
import sun.misc.Unsafe;

public class PluginImpl implements Plugin {
    private static final class Dummy {
        public boolean firstField;
    }

    private static final List<String> MODULES = List.of(
        "code", "comp", "file", "main", "model", "parser",
        "processing", "tree", "util", "jvm", "api"
    );

    private static void addOpens() {
        final var jdkCompilerModule = ModuleLayer.boot().findModule("jdk.compiler").orElseThrow();
        final var ownModule = PluginImpl.class.getModule();

        try {
            final var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            final var unsafe = (Unsafe) unsafeField.get(null);
            final var m = Module.class.getDeclaredMethod("implAddOpens", String.class, Module.class);
            unsafe.putBooleanVolatile(m, unsafe.objectFieldOffset(Dummy.class.getDeclaredField("firstField")), true);
            for (final var p : MODULES) {
                m.invoke(jdkCompilerModule, "com.sun.tools.javac." + p, ownModule);
            }
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getName() {
        return "coroutine-processor";
    }

    @Override
    public void init(final JavacTask javacTask, final String... strings) {
        addOpens();
        PluginDelegate.createInstance().init(javacTask, strings);
    }

    public boolean autoStart() {
        return true; // force the plugin to start automagically
    }
}
