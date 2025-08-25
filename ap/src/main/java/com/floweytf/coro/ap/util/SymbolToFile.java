package com.floweytf.coro.ap.util;

import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.jvm.ClassWriter;
import com.sun.tools.javac.util.Context;
import java.io.IOException;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

public class SymbolToFile {
    private final JavaFileManager fileManager;
    private final ClassWriter writer;

    public SymbolToFile(final Context context) {
        fileManager = context.get(JavaFileManager.class);
        writer = ClassWriter.instance(context);
    }

    /**
     * Gets the name of a class symbol. This is copied from {@link ClassWriter#writeClass(Symbol.ClassSymbol)}, and
     * should be kept up-to-date.
     *
     * @param symbol The symbol.
     * @return The class name.
     */
    private static String getClassName(final Symbol.ClassSymbol symbol) {
        return (symbol.owner.kind == Kinds.Kind.MDL ? symbol.name : symbol.flatname).toString();
    }

    /**
     * Obtains the output file for a specific class. This is copied from
     * {@link ClassWriter#writeClass(Symbol.ClassSymbol)}, and should be kept up-to-date.
     *
     * @param symbol The class.
     * @param name   The name of the class. This should be {@link SymbolToFile#getClassName}, but may be
     *               replaced with an inner class when emitting generated classes.
     * @return The file object representing the output file.
     */
    public JavaFileObject getClassFileFor(final Symbol.ClassSymbol symbol, final String name) {
        try {
            final JavaFileManager.Location outLocation;

            if (writer.multiModuleMode) {
                outLocation = fileManager.getLocationForModule(
                    StandardLocation.CLASS_OUTPUT,
                    Util.getModule(symbol).name.toString()
                );
            } else {
                outLocation = StandardLocation.CLASS_OUTPUT;
            }

            return fileManager.getJavaFileForOutput(outLocation, name, JavaFileObject.Kind.CLASS, symbol.sourcefile);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Obtains the output file for a specific class. This is copied from
     * {@link ClassWriter#writeClass(Symbol.ClassSymbol)}, and should be kept up-to-date.
     *
     * @param symbol The class.
     * @return The file object representing the output file.
     */
    public JavaFileObject getClassFileFor(final Symbol.ClassSymbol symbol) {
        return getClassFileFor(symbol, getClassName(symbol));
    }
}
