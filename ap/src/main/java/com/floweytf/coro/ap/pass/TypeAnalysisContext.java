package com.floweytf.coro.ap.pass;

import com.sun.tools.javac.code.ClassFinder;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Names;

public class TypeAnalysisContext {
    private final Symbol.ModuleSymbol moduleSymbol;
    private final Names names;
    private final Symtab symtab;
    private final Types types;
    private final ClassFinder classFinder;

    public TypeAnalysisContext(Symbol.ModuleSymbol moduleSymbol, Context context) {
        this.moduleSymbol = moduleSymbol;
        names = Names.instance(context);
        symtab = Symtab.instance(context);
        types = Types.instance(context);
        classFinder = ClassFinder.instance(context);
    }

    public Type lookup(String name) {
        final var flatName = names.fromString(name);

        final var res = symtab.getClass(moduleSymbol, flatName);
        if(res != null) {
            return res.type;
        }

        return classFinder.loadClass(moduleSymbol, flatName).type;
    }

    public boolean isAssignable(Type type, Type s) {
        return types.isAssignable(type, s);
    }
}
