package com.floweytf.coro.ap;

import com.floweytf.coro.ap.pass.TransformPass;
import com.floweytf.coro.ap.pass.ValidatePass;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JavacMessages;
import com.sun.tools.javac.util.Names;
import it.unimi.dsi.fastutil.Pair;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import org.jetbrains.annotations.NotNull;

public class Coroutines implements TaskListener {
    private final Context context;
    private final CoroNames coroNames;

    private final Map<Symbol, Pair<List<JCTree.JCMethodDecl>, List<JCTree.JCMethodDecl>>> coroutineMethods =
        new IdentityHashMap<>();

    public Coroutines(final Context context) {
        this.context = context;
        coroNames = new CoroNames(Names.instance(context));

        JavacMessages.instance(context).add(locale -> new ResourceBundle() {
            @Override
            protected Object handleGetObject(@NotNull final String key) {
                return "{0}";
            }

            @Override
            public @NotNull Enumeration<String> getKeys() {
                return Collections.enumeration(List.of(Constants.DIAGNOSTIC_KEY));
            }
        });
    }

    public void reportCoroutineMethod(final JCTree.JCMethodDecl decl, final CoroutineKind type) {
        final var pair = coroutineMethods.computeIfAbsent(decl.sym.owner, x -> Pair.of(
            new ArrayList<>(),
            new ArrayList<>()
        ));

        if (type == CoroutineKind.TASK) {
            pair.left().add(decl);
        } else {
            pair.right().add(decl);
        }
    }

    @Override
    public void finished(final TaskEvent event) {
        switch (event.getKind()) {
        case ANALYZE -> ((JCTree.JCCompilationUnit) event.getCompilationUnit()).accept(new ValidatePass(this, event));
        case COMPILATION -> new TransformPass(this).process(coroutineMethods);
        }
    }

    public Context getContext() {
        return context;
    }

    public CoroNames coroNames() {
        return coroNames;
    }
}
