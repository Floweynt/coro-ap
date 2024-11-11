package com.floweytf.coro.ap;

import com.floweytf.coro.ap.pass.CoroutineTransformer;
import com.floweytf.coro.ap.pass.ValidateCoro;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JavacMessages;
import com.sun.tools.javac.util.Names;
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
    private final CoroNames names;

    private final Map<Symbol, List<JCTree.JCMethodDecl>> coroutineMethods = new IdentityHashMap<>();

    public Coroutines(Context context) {
        this.context = context;
        names = new CoroNames(Names.instance(context));

        JavacMessages.instance(context).add(locale -> new ResourceBundle() {
            @Override
            protected Object handleGetObject(@NotNull String key) {
                return "{0}";
            }

            @Override
            public @NotNull Enumeration<String> getKeys() {
                return Collections.enumeration(List.of(Constants.DIAGNOSTIC_KEY));
            }
        });
    }

    public CoroNames names() {
        return names;
    }

    public void reportCoroutineMethod(JCTree.JCMethodDecl decl) {
        coroutineMethods.computeIfAbsent(decl.sym.owner, x -> new ArrayList<>()).add(decl);
    }

    @Override
    public void finished(TaskEvent event) {
        switch (event.getKind()) {
        case ANALYZE ->
            ((JCTree.JCCompilationUnit) event.getCompilationUnit()).accept(new ValidateCoro(this, event));
        case COMPILATION -> new CoroutineTransformer(this).process(coroutineMethods);
        }
    }

    public Context getContext() {
        return context;
    }
}
