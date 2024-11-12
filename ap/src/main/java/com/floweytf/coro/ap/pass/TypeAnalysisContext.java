package com.floweytf.coro.ap.pass;

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

    public TypeAnalysisContext(Symbol.ModuleSymbol moduleSymbol, Context context) {
        this.moduleSymbol = moduleSymbol;
        this.names = Names.instance(context);
        this.symtab = Symtab.instance(context);
        this.types = Types.instance(context);
    }

    public Type lookup(String name) {
        return symtab.getClass(moduleSymbol, names.fromString(name)).type;
    }

    public boolean isAssignable(Type type, Type s) {
        return types.isAssignable(type, s);
    }

    public boolean isSubtype(Type type, Type s) {
        return types.isSubtype(type, s);
    }
}
