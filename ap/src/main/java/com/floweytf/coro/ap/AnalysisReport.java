package com.floweytf.coro.ap;

import com.floweytf.coro.ap.util.DescriptorGenerator;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.LambdaToMethod;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCLambda;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import org.objectweb.asm.tree.MethodNode;

import static com.sun.tools.javac.code.Symbol.ClassSymbol;

public final class AnalysisReport {
    private final Reference2IntMap<ClassSymbol> classesWithCoroutines = new Reference2IntOpenHashMap<>();
    private final Map<ClassSymbol, List<JCLambda>> lambdaCoroutineMethods = new Reference2ObjectOpenHashMap<>();
    private final Set<ClassSymbol> generatedSymbols = new ReferenceOpenHashSet<>();
    private final Map<JCTree, Object> lambdaMeta = new Reference2ObjectOpenHashMap<>();
    private final Debug debug;
    private final Types types;
    private final DescriptorGenerator descGen;
    private final Field contextMapField;
    private final Field translatedSymField;
    private final LambdaToMethod lambdaToMethod;

    public interface Visitor {
        int visit(final ClassSymbol symbol, final Predicate<MethodNode> predicate) throws IOException;
    }

    public AnalysisReport(final Coroutines coroutines) {
        this.debug = coroutines.debug();
        this.types = Types.instance(coroutines.getContext());
        this.lambdaToMethod = LambdaToMethod.instance(coroutines.getContext());
        descGen = new DescriptorGenerator(types);

        try {
            contextMapField = LambdaToMethod.class.getDeclaredField("contextMap");
            contextMapField.setAccessible(true);

            translatedSymField =
                Class.forName("com.sun.tools.javac.comp" +
                        ".LambdaToMethod$LambdaAnalyzerPreprocessor$LambdaTranslationContext")
                    .getDeclaredField("translatedSym");
            translatedSymField.setAccessible(true);
        } catch (final ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public void reportCoroutineMethod(final ClassSymbol classSymbol) {
        classesWithCoroutines.computeInt(
            classSymbol,
            (ignored, value) -> (value == null ? 0 : value) + 1);
    }

    public void reportLambda(final ClassSymbol classSymbol, final JCLambda lambda) {
        lambdaCoroutineMethods.computeIfAbsent(classSymbol, ignored -> new ArrayList<>())
            .add(lambda);
    }

    @SuppressWarnings("unchecked")
    public void reportGeneratedSymbol(final ClassSymbol symbol) {
        generatedSymbols.add(symbol);
        try {
            lambdaMeta.putAll((Map<JCTree, Object>) contextMapField.get(lambdaToMethod));
        } catch (final IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public void accept(final Visitor visitor) throws Exception {
        final var lambdaNameSet = new ObjectOpenHashSet<String>();

        final var allTransformationTargets = new ReferenceOpenHashSet<ClassSymbol>();
        allTransformationTargets.addAll(classesWithCoroutines.keySet());
        allTransformationTargets.addAll(lambdaCoroutineMethods.keySet());

        for (final var symbol : allTransformationTargets) {
            final var regularCoroutineCount = classesWithCoroutines.getOrDefault(symbol, 0);

            if (!generatedSymbols.contains(symbol)) {
                debug.logPrintln("skipping class %s because it was not generated", symbol.fullname);
                continue;
            }

            lambdaNameSet.clear();
            final var lambdaMethods = lambdaCoroutineMethods.get(symbol);

            if (lambdaMethods != null) {
                for (final var lambda : lambdaMethods) {
                    final var info = Objects.requireNonNull(lambdaMeta.get(lambda));
                    final var methodSymbol = (MethodSymbol) translatedSymField.get(info);
                    lambdaNameSet.add(methodSymbol.name + descGen.generate(types.erasure(methodSymbol.type)));
                }
            }

            final var expectedCount = regularCoroutineCount + lambdaNameSet.size();
            final var transformedCount = visitor.visit(symbol, mn -> lambdaNameSet.contains(mn.name + mn.desc));

            debug.logPrintln(
                "transforming class %s: %s regular coroutines, %s lambdas (%s)",
                symbol.fullname,
                regularCoroutineCount,
                lambdaNameSet.size(),
                lambdaNameSet
            );

            if (transformedCount != expectedCount) {
                debug.warnPrintln(
                    "on class %s: expected %s methods transformed, only got %s",
                    symbol.fullname,
                    expectedCount,
                    transformedCount
                );
            }
        }
    }
}
