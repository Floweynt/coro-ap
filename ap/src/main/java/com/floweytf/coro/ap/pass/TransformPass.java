package com.floweytf.coro.ap.pass;

import com.floweytf.coro.ap.Coroutines;
import com.floweytf.coro.ap.util.Util;
import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.jvm.ClassWriter;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Name;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class TransformPass {
    private static class SigGenerator extends Types.SignatureGenerator {
        private final StringBuilder sb = new StringBuilder();

        private SigGenerator(Types types) {
            super(types);
        }

        protected void append(char ch) {
            this.sb.append(ch);
        }

        protected void append(byte[] ba) {
            this.sb.append(new String(ba));
        }

        protected void append(Name name) {
            this.sb.append(name.toString());
        }
    }

    private final boolean isCompileTask;
    private final JavaFileManager fileManager;
    private final ClassWriter writer;
    private final SigGenerator sigGenerator;
    private final Types types;

    public TransformPass(Coroutines coroutines) {
        isCompileTask = !JavaCompiler.instance(coroutines.getContext()).sourceOutput;
        fileManager = coroutines.getContext().get(JavaFileManager.class);
        writer = ClassWriter.instance(coroutines.getContext());
        types = Types.instance(coroutines.getContext());
        sigGenerator = new SigGenerator(types);
    }

    /**
     * Gets the name of a class symbol. This is copied from {@link ClassWriter#writeClass(Symbol.ClassSymbol)}, and
     * should be kept up-to-date.
     *
     * @param symbol The symbol.
     * @return The class name.
     */
    private String getClassName(Symbol.ClassSymbol symbol) {
        return (symbol.owner.kind == Kinds.Kind.MDL ? symbol.name : symbol.flatname).toString();
    }

    /**
     * Obtains the output file for a specific class. This is copied from
     * {@link ClassWriter#writeClass(Symbol.ClassSymbol)}, and should be kept up-to-date.
     *
     * @param symbol The class.
     * @param name   The name of the class. This should be {@link TransformPass#getClassName}, but may be
     *               replaced with an inner class when emitting generated classes.
     * @return The file object representing the output file.
     */
    private JavaFileObject getClassFileFor(Symbol.ClassSymbol symbol, String name) {
        try {
            JavaFileManager.Location outLocation;

            if (writer.multiModuleMode) {
                outLocation = fileManager.getLocationForModule(
                    StandardLocation.CLASS_OUTPUT,
                    Util.getModule(symbol).name.toString()
                );
            } else {
                outLocation = StandardLocation.CLASS_OUTPUT;
            }

            return fileManager.getJavaFileForOutput(outLocation, name, JavaFileObject.Kind.CLASS, symbol.sourcefile);
        } catch (IOException e) {
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
    private JavaFileObject getClassFileFor(Symbol.ClassSymbol symbol) {
        return getClassFileFor(symbol, getClassName(symbol));
    }

    /**
     * Obtains the binary name of a source-level {@link Type}.
     *
     * @param type The source level type.
     * @return The JVM typename.
     */
    private String getBinaryName(Type type) {
        sigGenerator.assembleSig(types.erasure(type));
        final var ret = sigGenerator.sb.toString();
        sigGenerator.sb.setLength(0);
        return ret;
    }

    /**
     * Emits a class node to a file.
     *
     * @param node The class.
     * @param file The output file.
     * @throws IOException When underlying IO operations fail.
     */
    private void writeClass(ClassNode node, JavaFileObject file) throws IOException {
        final var writer = new org.objectweb.asm.ClassWriter(org.objectweb.asm.ClassWriter.COMPUTE_FRAMES);

        node.accept(writer);

        try (final var stream = file.openOutputStream()) {
            stream.write(writer.toByteArray());
        }
    }

    public void process(Map<Symbol, List<JCTree.JCMethodDecl>> coroutineMethods, Context context) {
        // We don't care if this is a gen sources or gen javadoc task.
        if (!isCompileTask) {
            return;
        }

        coroutineMethods.forEach(((ownerSymbol, declaration) -> {
            try {
                // We should probably be smarter here.
                if (!(ownerSymbol instanceof Symbol.ClassSymbol classSymbol)) {
                    throw new IllegalStateException();
                }

                final var classFile = getClassFileFor(classSymbol);
                final var classNode = new ClassNode();

                try (final var is = classFile.openInputStream()) {
                    new ClassReader(is).accept(classNode, ClassReader.EXPAND_FRAMES);
                }

                // construct signature array
                final var coroMethodSignatures = declaration.stream()
                    .map(x -> x.getName() + getBinaryName(x.type))
                    .collect(Collectors.toUnmodifiableSet());

                int matchCount = 0;

                for (MethodNode method : classNode.methods) {
                    if (coroMethodSignatures.contains(method.name + method.desc)) {
                        final var genClass = MethodTransformer.generate(classNode, method, matchCount);
                        final var genOutput = getClassFileFor(classSymbol, genClass.name.replace('/', '.'));
                        writeClass(genClass, genOutput);
                        matchCount++;
                    }
                }

                if (matchCount != coroMethodSignatures.size()) {
                    throw new AssertionError("method number mismatch " + coroMethodSignatures);
                }

                writeClass(classNode, classFile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }));
    }
}
