package com.floweytf.coro.ap.pass;

import com.floweytf.coro.ap.AnalysisReport;
import com.floweytf.coro.ap.Constants;
import com.floweytf.coro.ap.Coroutines;
import com.floweytf.coro.ap.Debug;
import com.floweytf.coro.ap.codegen.MethodTransformer;
import com.floweytf.coro.ap.util.SymbolToFile;
import com.sun.tools.javac.main.JavaCompiler;
import java.io.IOException;
import javax.tools.JavaFileObject;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class TransformPass {
    private final boolean isCompileTask;
    private final SymbolToFile symbolToFile;
    private final Debug debug;

    public TransformPass(final Coroutines coroutines) {
        isCompileTask = !JavaCompiler.instance(coroutines.getContext()).sourceOutput;
        symbolToFile = new SymbolToFile(coroutines.getContext());
        debug = coroutines.debug();
    }

    /**
     * Emits a class node to a file.
     *
     * @param node The class.
     * @param file The output file.
     * @throws IOException When underlying IO operations fail.
     */
    private static void writeClass(final ClassNode node, final JavaFileObject file) throws IOException {
        final var writer = new org.objectweb.asm.ClassWriter(org.objectweb.asm.ClassWriter.COMPUTE_MAXS);

        node.accept(writer);

        try (final var stream = file.openOutputStream()) {
            stream.write(writer.toByteArray());
        }
    }

    public void process(final AnalysisReport coroutineMethods) throws Exception {
        // We don't care if this is a gen sources or gen javadoc task.
        if (!isCompileTask) {
            debug.logPrintln("TransformPass#process: skipping because not a compile task");
            return;
        }

        coroutineMethods.accept((classSymbol, predicate) -> {
            final var file = symbolToFile.getClassFileFor(classSymbol);
            final var classNode = new ClassNode();

            try (final var is = file.openInputStream()) {
                new ClassReader(is).accept(classNode, ClassReader.EXPAND_FRAMES);
            }

            int transformedCount = 0;

            for (final MethodNode method : classNode.methods) {
                final var isAnnotated = method.invisibleAnnotations != null &&
                    method.invisibleAnnotations.stream().anyMatch(x -> x.desc.equals(Constants.COROUTINE_ANN_DESC));

                if (!isAnnotated && !predicate.test(method)) {
                    continue;
                }

                final var genClass = new MethodTransformer(classNode, method, transformedCount).generate(debug);
                final var genOutput = symbolToFile.getClassFileFor(classSymbol, genClass.name.replace('/', '.'));
                writeClass(genClass, genOutput);
                transformedCount++;
            }

            writeClass(classNode, file);

            return transformedCount;
        });
    }
}
