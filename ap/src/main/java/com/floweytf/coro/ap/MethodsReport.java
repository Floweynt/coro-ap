package com.floweytf.coro.ap;

import com.sun.tools.javac.code.Symbol;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public record MethodsReport(
    Set<Symbol.ClassSymbol> classesWithCoroutines,
    Map<Symbol.ClassSymbol, IntList> lambdaCoroutineMethods
) {
    public interface Visitor {
        void visit(final Symbol.ClassSymbol symbol, final Predicate<String> isLambdaCoroutine) throws IOException;
    }

    public void accept(final Visitor visitor) throws IOException {
        final var universe = new ReferenceOpenHashSet<Symbol.ClassSymbol>();
        universe.addAll(classesWithCoroutines);
        universe.addAll(lambdaCoroutineMethods.keySet());

        for (final var symbol : universe) {
            final var lambdaIds = new IntOpenHashSet(lambdaCoroutineMethods.getOrDefault(symbol, IntList.of()));

            visitor.visit(
                symbol,
                s -> s.startsWith("lambda$") &&
                    lambdaIds.contains(Integer.parseInt(s.substring(s.lastIndexOf("$") + 1)))
            );
        }
    }
}
