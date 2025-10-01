package com.floweytf.coro.ap;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class Debug {
    private record Matcher(Pattern classPattern, Pattern methodPattern) {
        public boolean test(final ClassNode cn, final MethodNode mn) {
            return classPattern.matcher(cn.name).matches() &&
                methodPattern.matcher(mn.name).matches();
        }
    }

    private final List<Matcher> matchers = new ArrayList<>();
    private final boolean shouldLog = Boolean.getBoolean("coro.debug.log");
    private final boolean shouldPerf = Boolean.getBoolean("coro.debug.perf");

    public Debug() {
        final var dumps = System.getProperty("coro.debug.dump");

        if (dumps == null || dumps.isBlank()) {
            return;
        }

        final var entries = dumps.split(",");

        for (final var entry : entries) {
            final var parts = entry.split("::");
            final String className = parts[0];
            final String methodName = parts.length > 1 ? parts[1] : "*";
            matchers.add(new Matcher(parseGlob(className), parseGlob(methodName)));
        }
    }

    @Nullable
    private static Pattern parseGlob(final String glob) {
        final var regex = new StringBuilder();

        regex.append("^");
        for (int i = 0; i < glob.length(); i++) {
            final char c = glob.charAt(i);
            if (c == '*') {
                if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                    regex.append(".*");
                    i++;
                } else {
                    regex.append("[^/]*");
                }
            } else if (c == '.') {
                regex.append('/');
            } else if (Character.isJavaIdentifierPart(c)) {
                regex.append(c);
            } else {
                System.out.printf("warn: bad character in glob '%s': '%s'\n", glob, c);
                return null;
            }
        }
        regex.append("$");

        return Pattern.compile(regex.toString());
    }

    public boolean shouldDump(final ClassNode cn, final MethodNode mn) {
        for (final var matcher : matchers) {
            if (matcher.test(cn, mn)) {
                return true;
            }
        }

        return false;
    }

    public void logPrintln(@PrintFormat final String fmt, final Object... args) {
        if (shouldLog) {
            System.out.printf(fmt + "\n", args);
        }
    }

    public void perfPrintln(@PrintFormat final String fmt, final Object... args) {
        if (shouldPerf) {
            System.out.printf(fmt + "\n", args);
        }
    }

    public void dump(final Consumer<PrintWriter> handler) {
        handler.accept(new PrintWriter(System.out));
    }

    public void warnPrintln(@PrintFormat final String fmt, final Object... args) {
        System.out.printf(fmt + "\n", args);
    }
}
